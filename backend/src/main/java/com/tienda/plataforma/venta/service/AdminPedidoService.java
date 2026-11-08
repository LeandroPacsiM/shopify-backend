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
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoItem;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
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

	public AdminPedidoService(PedidoRepository pedidos, PedidoItemRepository pedidoItems,
			HistorialEstadoPedidoRepository historial, NivelInventarioRepository niveles,
			MovimientoInventarioRepository movimientos, NotificacionService notificaciones) {
		this.pedidos = pedidos;
		this.pedidoItems = pedidoItems;
		this.historial = historial;
		this.niveles = niveles;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
	}

	@Transactional(readOnly = true)
	public List<PedidoResponse> listar(Long tiendaId, String estado) {
		return pedidos.findByTiendaId(tiendaId).stream()
				.filter(p -> estado == null || estado.equals(p.getEstado()))
				.map(p -> new PedidoResponse(p.getId(), p.getNumeroPedido(), p.getEstado(), p.getTotal()))
				.toList();
	}

	/** Cancela y libera reservas. Solo pendiente o pagado (Shopify: no parcial/shipped). */
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
		liberarReservas(pedido);
		String anterior = pedido.getEstado();
		pedido.setEstado(Pedido.CANCELADO);
		pedido.setActualizadoEn(Instant.now());
		pedidos.save(pedido);
		historial(pedido, anterior, Pedido.CANCELADO, "Cancelado por la tienda");
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
		return new PedidoResponse(pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado(), pedido.getTotal());
	}

	private void liberarReservas(Pedido pedido) {
		for (MovimientoInventario mov : movimientos.findByReferencia(pedido.getNumeroPedido())) {
			if (!MovimientoInventario.RESERVA.equals(mov.getTipoMovimiento())) {
				continue;
			}
			NivelInventario nivel = niveles.findConBloqueoById(mov.getNivel().getId())
					.orElseThrow(() -> new NoEncontradoException("nivel"));
			nivel.setReservado(Math.max(0, nivel.getReservado() - mov.getCantidad()));
			nivel.setActualizadoEn(Instant.now());
			niveles.save(nivel);

			MovimientoInventario liberacion = new MovimientoInventario();
			liberacion.setNivel(nivel);
			liberacion.setTipoMovimiento(MovimientoInventario.LIBERACION);
			liberacion.setCantidad(mov.getCantidad());
			liberacion.setMotivo("Cancelación del pedido " + pedido.getNumeroPedido());
			liberacion.setReferencia(pedido.getNumeroPedido());
			liberacion.setCreadoEn(Instant.now());
			movimientos.save(liberacion);
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
