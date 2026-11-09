package com.tienda.plataforma.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.realtime.dto.NoLeidasResponse;
import com.tienda.plataforma.realtime.dto.NotificacionResponse;
import com.tienda.plataforma.realtime.entity.Notificacion;
import com.tienda.plataforma.realtime.repository.NotificacionRepository;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * Campana admin por tienda + bandeja persistente (V16).
 * Cada evento se guarda en `notificaciones` (fuente que la bandeja lee)
 * y además se avisa por WS en vivo (ver diagramas/09-websocket.md).
 * Único uso de WS; checkout/tracking siguen por polling/webhooks.
 */
@Service
public class NotificacionService {

	public static final String NUEVO_PEDIDO = "nuevo_pedido";
	public static final String PEDIDO_PAGADO = "pedido_pagado";
	public static final String PEDIDO_CANCELADO = "pedido_cancelado";
	public static final String PEDIDO_ENTREGADO = "pedido_entregado";
	public static final String STOCK_BAJO = "stock_bajo";
	/** Avisos leídos con más que esto se purgan (los no leídos quedan). */
	public static final Duration PURGA_GRACIA = Duration.ofDays(90);

	private final SimpMessagingTemplate mensajeria;
	private final NotificacionRepository notificaciones;
	private final TiendaRepository tiendas;

	public NotificacionService(SimpMessagingTemplate mensajeria,
			NotificacionRepository notificaciones, TiendaRepository tiendas) {
		this.mensajeria = mensajeria;
		this.notificaciones = notificaciones;
		this.tiendas = tiendas;
	}

	/**
	 * Persiste el aviso (en la TX del llamante, como la auditoría) y lo
	 * emite por WS con su id. Los datos no llevan secretos (solo ids).
	 */
	@Transactional
	public NotificacionResponse notificar(Long tiendaId, String tipo, Map<String, Object> datos) {
		Notificacion aviso = new Notificacion();
		aviso.setTienda(tiendas.getReferenceById(tiendaId));
		aviso.setTipo(tipo);
		if (datos.get("pedidoId") instanceof Number pedidoId) {
			aviso.setEntidad("pedido");
			aviso.setEntidadId(pedidoId.longValue());
		}
		aviso.setDatos(datos);
		notificaciones.save(aviso);
		Object mensaje = Map.of("id", aviso.getId(), "tipo", tipo,
				"cuando", Instant.now().toString(), "datos", datos);
		mensajeria.convertAndSend("/topic/tienda/" + tiendaId, mensaje);
		return responder(aviso);
	}

	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<NotificacionResponse> listar(Long tiendaId,
			Integer pagina, Integer tamano) {
		int page = pagina == null || pagina < 0 ? 0 : pagina;
		int size = tamano == null ? 20 : Math.min(Math.max(tamano, 1), 50);
		var paginacion = org.springframework.data.domain.PageRequest.of(page, size,
				org.springframework.data.domain.Sort.by(
						org.springframework.data.domain.Sort.Direction.DESC, "creadoEn"));
		return notificaciones.findByTiendaId(tiendaId, paginacion).map(this::responder);
	}

	@Transactional(readOnly = true)
	public NoLeidasResponse contarNoLeidas(Long tiendaId) {
		return new NoLeidasResponse(notificaciones.countByTiendaIdAndLeidaEnIsNull(tiendaId));
	}

	/** Marca leída; de otra tienda responde 404 (sin fuga, como pedidos). */
	@Transactional
	public NotificacionResponse marcarLeida(Long tiendaId, Long id) {
		Notificacion aviso = notificaciones.findById(id)
				.orElseThrow(() -> new NoEncontradoException("notificacion"));
		if (!aviso.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("notificacion");
		}
		aviso.setLeidaEn(Instant.now());
		notificaciones.save(aviso);
		return responder(aviso);
	}

	/**
	 * Purga avisos leídos hace más de la gracia (domingos). Los no leídos
	 * quedan y `suscripcion_eventos` nunca se purga (es dedup: borrarlo
	 * reabriría cobros duplicados).
	 */
	@Scheduled(cron = "0 0 4 * * SUN")
	@Transactional
	public void purgarLeidas() {
		notificaciones.deleteByLeidaEnBefore(Instant.now().minus(PURGA_GRACIA));
	}

	private NotificacionResponse responder(Notificacion aviso) {
		return new NotificacionResponse(aviso.getId(), aviso.getTipo(),
				aviso.getEntidad(), aviso.getEntidadId(), aviso.getDatos(),
				aviso.getLeidaEn(), aviso.getCreadoEn());
	}
}
