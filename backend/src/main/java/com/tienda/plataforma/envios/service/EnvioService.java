package com.tienda.plataforma.envios.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.envios.dto.CrearEnvioRequest;
import com.tienda.plataforma.envios.dto.EnvioResponse;
import com.tienda.plataforma.envios.dto.TrackingWriteRequest;
import com.tienda.plataforma.envios.entity.Envio;
import com.tienda.plataforma.envios.entity.EnvioEvento;
import com.tienda.plataforma.envios.entity.EnvioItem;
import com.tienda.plataforma.envios.entity.Transportista;
import com.tienda.plataforma.envios.repository.EnvioEventoRepository;
import com.tienda.plataforma.envios.repository.EnvioItemRepository;
import com.tienda.plataforma.envios.repository.EnvioRepository;
import com.tienda.plataforma.envios.repository.TransportistaRepository;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.entity.MovimientoInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.entity.Ubicacion;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.inventario.repository.MovimientoInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.inventario.repository.UbicacionRepository;
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoItem;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoItemRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/**
 * RF-SHIP: envíos parciales con tope por línea y tracking mapeado (diagrama 07, V6).
 */
@Service
public class EnvioService {

	private final EnvioRepository envios;
	private final EnvioItemRepository envioItems;
	private final EnvioEventoRepository envioEventos;
	private final TransportistaRepository transportistas;
	private final UbicacionRepository ubicaciones;
	private final PedidoRepository pedidos;
	private final PedidoItemRepository pedidoItems;
	private final HistorialEstadoPedidoRepository historial;
	private final NotificacionService notificaciones;
	private final ArticuloInventarioRepository articulos;
	private final NivelInventarioRepository niveles;
	private final MovimientoInventarioRepository movimientos;
	private final SecureRandom aleatorio = new SecureRandom();

	public EnvioService(EnvioRepository envios, EnvioItemRepository envioItems,
			EnvioEventoRepository envioEventos, TransportistaRepository transportistas,
			UbicacionRepository ubicaciones, PedidoRepository pedidos, PedidoItemRepository pedidoItems,
			HistorialEstadoPedidoRepository historial, NotificacionService notificaciones,
			ArticuloInventarioRepository articulos, NivelInventarioRepository niveles,
			MovimientoInventarioRepository movimientos) {
		this.envios = envios;
		this.envioItems = envioItems;
		this.envioEventos = envioEventos;
		this.transportistas = transportistas;
		this.ubicaciones = ubicaciones;
		this.pedidos = pedidos;
		this.pedidoItems = pedidoItems;
		this.historial = historial;
		this.notificaciones = notificaciones;
		this.articulos = articulos;
		this.niveles = niveles;
		this.movimientos = movimientos;
	}

	@Transactional
	public EnvioResponse crear(Long tiendaId, Long pedidoId, CrearEnvioRequest req) {
		Pedido pedido = pedidos.findConBloqueoById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		if (!pedido.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("pedido");
		}
		if (Pedido.CANCELADO.equals(pedido.getEstado()) || Pedido.ENTREGADO.equals(pedido.getEstado())
				|| Pedido.REEMBOLSADO.equals(pedido.getEstado())) {
			throw new ConflictoException("pedido no despachable");
		}
		if (Pedido.PENDIENTE.equals(pedido.getEstado())) {
			throw new ConflictoException("pedido no pagado");
		}
		Transportista transportista = req.transportistaId() == null ? null
				: transportistas.findById(req.transportistaId())
						.orElseThrow(() -> new NoEncontradoException("transportista"));
		Ubicacion origen = req.ubicacionOrigenId() == null ? null
				: ubicaciones.findById(req.ubicacionOrigenId())
						.orElseThrow(() -> new NoEncontradoException("ubicación"));
		if (origen != null && !origen.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("ubicación");
		}

		Envio envio = new Envio();
		envio.setPedido(pedido);
		envio.setTransportista(transportista);
		envio.setUbicacionOrigen(origen);
		envio.setNumeroTracking("TRK-" + Math.abs(aleatorio.nextInt()));
		envio.setEstado(Envio.PREPARANDO);
		envio.setCreadoEn(Instant.now());
		envios.save(envio);

		for (var item : req.items()) {
			PedidoItem linea = pedidoItems.findById(item.pedidoItemId())
					.orElseThrow(() -> new NoEncontradoException("línea"));
			if (!linea.getPedido().getId().equals(pedidoId)) {
				throw new ConflictoException("línea de otro pedido");
			}
			int yaEnviado = envioItems.findByPedidoItemId(linea.getId()).stream()
					.mapToInt(EnvioItem::getCantidad).sum();
			if (yaEnviado + item.cantidad() > linea.getCantidad()) {
				throw new ConflictoException("excede lo comprado: " + linea.getSku());
			}
			EnvioItem fila = new EnvioItem();
			fila.setEnvio(envio);
			fila.setPedidoItem(linea);
			fila.setCantidad(item.cantidad());
			envioItems.save(fila);
		}
		for (EnvioItem fila : envioItems.findByEnvioId(envio.getId())) {
			consumirReserva(pedido, envio, fila);
		}
		if (Pedido.PAGADO.equals(pedido.getEstado())) {
			transicion(pedido, Pedido.PAGADO, Pedido.EN_PREPARACION);
		}
		return new EnvioResponse(envio.getId(), envio.getNumeroTracking(), envio.getEstado());
	}

	/**
	 * Convierte la reserva del checkout en salida: descuenta disponible y
	 * reservado una sola vez por unidad despachada (flujo 8, RF-SHIP-01).
	 */
	private void consumirReserva(Pedido pedido, Envio envio, EnvioItem fila) {
		PedidoItem linea = fila.getPedidoItem();
		if (linea.getVariante() == null) {
			return;
		}
		ArticuloInventario articulo = articulos.findByVarianteId(linea.getVariante().getId()).orElse(null);
		if (articulo == null || !Boolean.TRUE.equals(articulo.getControlaStock())
				|| !Boolean.TRUE.equals(articulo.getRequiereEnvio())) {
			return;
		}
		int pendiente = fila.getCantidad();
		for (NivelInventario candidato : niveles.findByArticuloId(articulo.getId())) {
			if (pendiente <= 0) {
				break;
			}
			NivelInventario nivel = niveles.findConBloqueoById(candidato.getId())
					.orElseThrow(() -> new NoEncontradoException("nivel"));
			int toma = Math.min(pendiente, nivel.getReservado());
			if (toma <= 0) {
				continue;
			}
			nivel.setReservado(nivel.getReservado() - toma);
			nivel.setDisponible(Math.max(0, nivel.getDisponible() - toma));
			nivel.setActualizadoEn(Instant.now());
			niveles.save(nivel);

			MovimientoInventario salida = new MovimientoInventario();
			salida.setNivel(nivel);
			salida.setTipoMovimiento(MovimientoInventario.SALIDA);
			salida.setCantidad(toma);
			salida.setMotivo("Despacho del pedido " + pedido.getNumeroPedido()
					+ " envío " + envio.getId());
			salida.setReferencia(pedido.getNumeroPedido());
			salida.setCreadoEn(Instant.now());
			movimientos.save(salida);
			pendiente -= toma;
		}
		if (pendiente > 0) {
			throw new ConflictoException("sin reserva para despachar: " + linea.getSku());
		}
	}

	/** Aplica un evento ya autenticado; serializa por envío, deduplica y ordena por `ocurrido_en`. */
	@Transactional
	public void tracking(TrackingWriteRequest req) {
		String referencia = req.referencia() == null ? null : req.referencia().trim();
		if (referencia == null || referencia.isBlank()) {
			throw new ConflictoException("referencia externa requerida");
		}
		Envio envio = envios.findConBloqueoByNumeroTracking(req.numeroTracking().trim())
				.orElseThrow(() -> new NoEncontradoException("envío"));
		Instant ocurrido = req.ocurridoEn() == null ? Instant.now() : req.ocurridoEn();
		if (envioEventos.existsByEnvioIdAndReferenciaExterna(envio.getId(), referencia)) {
			return;
		}
		Instant ultimo = envioEventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).stream()
				.map(EnvioEvento::getOcurridoEn)
				.filter(java.util.Objects::nonNull)
				.max(Instant::compareTo).orElse(null);
		EnvioEvento evento = new EnvioEvento();
		evento.setEnvio(envio);
		String estadoExterno = req.estado().trim();
		evento.setEstado(estadoExterno);
		evento.setDescripcion(req.descripcion());
		evento.setUbicacion(req.ubicacion());
		evento.setOcurridoEn(ocurrido);
		evento.setReferenciaExterna(referencia);
		evento.setCreadoEn(Instant.now());
		envioEventos.save(evento);
		if (ultimo != null && ocurrido.isBefore(ultimo)) {
			return;
		}

		String mapeado = mapear(estadoExterno);
		if (mapeado != null && !mapeado.equals(envio.getEstado())) {
			envio.setEstado(mapeado);
			if (Envio.ENVIADO.equals(mapeado)) {
				envio.setEnviadoEn(ocurrido);
			}
			if (Envio.ENTREGADO.equals(mapeado)) {
				envio.setEntregadoEn(ocurrido);
			}
			envios.save(envio);
		}
		if (Envio.ENVIADO.equals(envio.getEstado()) || Envio.EN_TRANSITO.equals(envio.getEstado())
				|| Envio.EN_REPARTO.equals(envio.getEstado())) {
			Pedido enRuta = pedidos.findConBloqueoById(envio.getPedido().getId())
					.orElseThrow(() -> new NoEncontradoException("pedido"));
			if (Pedido.PAGADO.equals(enRuta.getEstado()) || Pedido.EN_PREPARACION.equals(enRuta.getEstado())) {
				transicion(enRuta, enRuta.getEstado(), Pedido.ENVIADO);
			}
		}
		if (Envio.ENTREGADO.equals(envio.getEstado())) {
			Pedido pedido = pedidos.findConBloqueoById(envio.getPedido().getId())
					.orElseThrow(() -> new NoEncontradoException("pedido"));
			boolean todoEntregado = envios.findByPedidoId(pedido.getId()).stream()
					.allMatch(e -> Envio.ENTREGADO.equals(e.getEstado()));
			if (todoEntregado && !Pedido.ENTREGADO.equals(pedido.getEstado())) {
				transicion(pedido, pedido.getEstado(), Pedido.ENTREGADO);
				notificaciones.notificar(pedido.getTienda().getId(), NotificacionService.PEDIDO_ENTREGADO,
						Map.of("pedidoId", pedido.getId(), "numero", pedido.getNumeroPedido()));
			}
		}
	}

	private void transicion(Pedido pedido, String anterior, String nuevo) {
		pedido.setEstado(nuevo);
		pedido.setActualizadoEn(Instant.now());
		pedidos.save(pedido);
		HistorialEstadoPedido h = new HistorialEstadoPedido();
		h.setPedido(pedido);
		h.setEstadoAnterior(anterior);
		h.setEstadoNuevo(nuevo);
		h.setCreadoEn(Instant.now());
		historial.save(h);
	}

	private String mapear(String externo) {
		if (externo == null) {
			return null;
		}
		return switch (externo.trim().toLowerCase(Locale.ROOT)) {
			case "preparando", "preparing" -> Envio.PREPARANDO;
			case "enviado", "shipped", "salida" -> Envio.ENVIADO;
			case "en_transito", "en transito", "transito", "in_transit" -> Envio.EN_TRANSITO;
			case "en_reparto", "reparto", "out_for_delivery" -> Envio.EN_REPARTO;
			case "entregado", "delivered" -> Envio.ENTREGADO;
			case "devuelto", "returned" -> Envio.DEVUELTO;
			case "cancelado", "canceled", "cancelled" -> Envio.CANCELADO;
			default -> null;
		};
	}
}
