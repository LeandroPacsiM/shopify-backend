package com.tienda.plataforma.venta.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.inventario.entity.MovimientoInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.repository.MovimientoInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.ReembolsoRepository;
import com.tienda.plataforma.envios.repository.EnvioRepository;
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoItem;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoDescuentoRepository;
import com.tienda.plataforma.venta.repository.PedidoItemRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** RF-ADMIN-03 y RF-ORDER-02: operar pedidos de la tienda (listar, cancelar, archivar). */
@Service
public class AdminPedidoService {

	private final PedidoRepository pedidos;
	private final PedidoItemRepository pedidoItems;
	private final HistorialEstadoPedidoRepository historial;
	private final NivelInventarioRepository niveles;
	private final MovimientoInventarioRepository movimientos;
	private final NotificacionService notificaciones;
	private final PagoRepository pagos;
	private final ReembolsoRepository reembolsos;
	private final PedidoDescuentoRepository pedidoDescuentos;
	private final DescuentoRepository descuentos;
	private final EnvioRepository envios;
	private final AuditoriaService auditoria;

	public AdminPedidoService(PedidoRepository pedidos, PedidoItemRepository pedidoItems,
			HistorialEstadoPedidoRepository historial, NivelInventarioRepository niveles,
			MovimientoInventarioRepository movimientos, NotificacionService notificaciones,
			PagoRepository pagos, ReembolsoRepository reembolsos,
			PedidoDescuentoRepository pedidoDescuentos, DescuentoRepository descuentos,
			EnvioRepository envios, AuditoriaService auditoria) {
		this.pedidos = pedidos;
		this.pedidoItems = pedidoItems;
		this.historial = historial;
		this.niveles = niveles;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pagos = pagos;
		this.reembolsos = reembolsos;
		this.pedidoDescuentos = pedidoDescuentos;
		this.descuentos = descuentos;
		this.envios = envios;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<PedidoResponse> listar(Long tiendaId, String estado,
			Integer pagina, Integer tamano) {
		int page = pagina == null || pagina < 0 ? 0 : pagina;
		int size = tamano == null ? 20 : Math.min(Math.max(tamano, 1), 50);
		var paginacion = org.springframework.data.domain.PageRequest.of(page, size,
				org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
		var paginaPedidos = estado == null
				? pedidos.findByTiendaId(tiendaId, paginacion)
				: pedidos.findByTiendaIdAndEstado(tiendaId, estado, paginacion);
		return paginaPedidos.map(p -> new PedidoResponse(p.getId(), p.getNumeroPedido(), p.getEstado(), p.getTotal()));
	}

	/**
	 * Cancela un pedido pendiente sin cobro. Un pedido pagado con saldo
	 * cobrado neto o reembolso en curso se rechaza: primero se reembolsa por
	 * `/api/admin/pagos/{id}/reembolsos` y el webhook lo lleva a `reembolsado`.
	 * Sin despacho previo (Shopify: no cancela parcial/enviado).
	 */
	@Transactional
	public PedidoResponse cancelar(Long tiendaId, Long pedidoId) {
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		if (!pedido.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("pedido");
		}
		if (!Pedido.PENDIENTE.equals(pedido.getEstado()) && !Pedido.PAGADO.equals(pedido.getEstado())) {
			throw new ConflictoException("solo pendiente o pagado se cancela");
		}
		if (!envios.findByPedidoId(pedidoId).isEmpty()) {
			throw new ConflictoException("pedido con despacho no se cancela");
		}
		java.math.BigDecimal cobrado = pagos.findByPedidoId(pedidoId).stream()
				.filter(p -> Pago.APROBADO.equals(p.getEstado()))
				.map(Pago::getMonto).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		java.math.BigDecimal devuelto = reembolsos.findByPedidoId(pedidoId).stream()
				.filter(r -> Reembolso.APROBADO.equals(r.getEstado()))
				.map(Reembolso::getMonto).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		boolean enCurso = reembolsos.findByPedidoId(pedidoId).stream()
				.anyMatch(r -> Reembolso.PENDIENTE.equals(r.getEstado())
						|| Reembolso.PROCESANDO.equals(r.getEstado()));
		if (enCurso) {
			throw new ConflictoException("reembolso en curso");
		}
		if (cobrado.subtract(devuelto).signum() > 0) {
			throw new ConflictoException("pedido con cobro aprobado: reembolsa antes de cancelar");
		}
		liberarReservas(pedido);
		revertirDescuentos(pedidoId);
		String anterior = pedido.getEstado();
		pedido.setEstado(Pedido.CANCELADO);
		pedido.setActualizadoEn(Instant.now());
		pedidos.save(pedido);
		historial(pedido, anterior, Pedido.CANCELADO, "Cancelado por la tienda");
		auditoria.registrar("pedidos", pedidoId, "pedido.cancelar");
		notificaciones.notificar(tiendaId, NotificacionService.PEDIDO_CANCELADO,
				Map.of("pedidoId", pedido.getId(), "numero", pedido.getNumeroPedido()));
		return new PedidoResponse(pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado(), pedido.getTotal());
	}

	@Transactional
	public PedidoResponse archivar(Long tiendaId, Long pedidoId, boolean archivado) {
		Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() -> new NoEncontradoException("pedido"));
		if (!pedido.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("pedido");
		}
		pedido.setArchivado(archivado);
		pedido.setArchivadoEn(archivado ? Instant.now() : null);
		pedido.setActualizadoEn(Instant.now());
		pedidos.save(pedido);
		auditoria.registrar("pedidos", pedidoId, archivado ? "pedido.archivar" : "pedido.desarchivar");
		return new PedidoResponse(pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado(), pedido.getTotal());
	}

	private void liberarReservas(Pedido pedido) {
		for (MovimientoInventario mov : movimientos.findByReferencia(pedido.getNumeroPedido())) {
			if (!MovimientoInventario.RESERVA.equals(mov.getTipoMovimiento())) {
				continue;
			}
			NivelInventario nivel = niveles.findConBloqueoById(mov.getNivel().getId())
					.orElseThrow(() -> new NoEncontradoException("nivel"));
			int liberar = Math.min(mov.getCantidad(), nivel.getReservado());
			if (liberar <= 0) {
				continue;
			}
			nivel.setReservado(nivel.getReservado() - liberar);
			nivel.setActualizadoEn(Instant.now());
			niveles.save(nivel);

			MovimientoInventario liberacion = new MovimientoInventario();
			liberacion.setNivel(nivel);
			liberacion.setTipoMovimiento(MovimientoInventario.LIBERACION);
			liberacion.setCantidad(liberar);
			liberacion.setMotivo("Cancelación del pedido " + pedido.getNumeroPedido());
			liberacion.setReferencia(pedido.getNumeroPedido());
			liberacion.setCreadoEn(Instant.now());
			movimientos.save(liberacion);
		}
	}

	private void revertirDescuentos(Long pedidoId) {
		for (var aplicacion : pedidoDescuentos.findByPedidoId(pedidoId)) {
			descuentos.findConBloqueoById(aplicacion.getDescuento().getId()).ifPresent(d -> {
				d.setUsosActuales(Math.max(0, d.getUsosActuales() - 1));
				descuentos.save(d);
			});
		}
	}

	private void historial(Pedido pedido, String anterior, String nuevo, String comentario) {
		HistorialEstadoPedido h = new HistorialEstadoPedido();
		h.setPedido(pedido);
		h.setEstadoAnterior(anterior);
		h.setEstadoNuevo(nuevo);
		h.setComentario(comentario);
		h.setCreadoEn(Instant.now());
		historial.save(h);
	}
}
