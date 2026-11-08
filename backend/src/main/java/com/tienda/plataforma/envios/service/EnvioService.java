package com.tienda.plataforma.envios.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
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
import com.tienda.plataforma.inventario.entity.Ubicacion;
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
	private final SecureRandom aleatorio = new SecureRandom();

	public EnvioService(EnvioRepository envios, EnvioItemRepository envioItems,
			EnvioEventoRepository envioEventos, TransportistaRepository transportistas,
			UbicacionRepository ubicaciones, PedidoRepository pedidos, PedidoItemRepository pedidoItems,
			HistorialEstadoPedidoRepository historial, NotificacionService notificaciones) {
		this.envios = envios;
		this.envioItems = envioItems;
		this.envioEventos = envioEventos;
		this.transportistas = transportistas;
		this.ubicaciones = ubicaciones;
		this.pedidos = pedidos;
		this.pedidoItems = pedidoItems;
		this.historial = historial;
		this.notificaciones = notificaciones;
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
		if (Pedido.PAGADO.equals(pedido.getEstado())) {
			transicion(pedido, Pedido.PAGADO, Pedido.EN_PREPARACION);
		}
		return new EnvioResponse(envio.getId(), envio.getNumeroTracking(), envio.getEstado());
	}

	/** Tracking del transportista: guarda el evento y mapea al estado local. */
	@Transactional
	public void tracking(TrackingWriteRequest req) {
		Envio envio = envios.findByNumeroTracking(req.numeroTracking())
				.orElseThrow(() -> new NoEncontradoException("envío"));
		Instant ocurrido = req.ocurridoEn() == null ? Instant.now() : req.ocurridoEn();
		if (req.referencia() != null && envioEventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).stream()
				.anyMatch(e -> req.referencia().equals(e.getReferenciaExterna()))) {
			return;
		}
		EnvioEvento evento = new EnvioEvento();
		evento.setEnvio(envio);
		evento.setEstado(req.estado());
		evento.setDescripcion(req.descripcion());
		evento.setUbicacion(req.ubicacion());
		evento.setOcurridoEn(ocurrido);
		evento.setReferenciaExterna(req.referencia());
		evento.setCreadoEn(Instant.now());
		envioEventos.save(evento);

		String mapeado = mapear(req.estado());
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
		if (Envio.ENTREGADO.equals(envio.getEstado())) {
			Pedido pedido = envio.getPedido();
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
		return switch (externo.toLowerCase()) {
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
