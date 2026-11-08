package com.tienda.plataforma.venta.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.envios.entity.Envio;
import com.tienda.plataforma.envios.repository.EnvioEventoRepository;
import com.tienda.plataforma.envios.repository.EnvioRepository;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.dto.TrackingResponse;
import com.tienda.plataforma.venta.dto.TrackingResponse.EnvioTracking;
import com.tienda.plataforma.venta.dto.TrackingResponse.EventoTracking;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** RF-SHIP-02 (lectura): tracking por polling. Sin tienda o sin ser dueño → no existe / prohibido. */
@Service
public class TrackingService {

	private final PedidoRepository pedidos;
	private final EnvioRepository envios;
	private final EnvioEventoRepository eventos;
	private final TiendaRepository tiendas;

	public TrackingService(PedidoRepository pedidos, EnvioRepository envios,
			EnvioEventoRepository eventos, TiendaRepository tiendas) {
		this.pedidos = pedidos;
		this.envios = envios;
		this.eventos = eventos;
		this.tiendas = tiendas;
	}

	@Transactional(readOnly = true)
	public TrackingResponse tracking(Long usuarioId, String slug, Long pedidoId) {
		Long tiendaId = tiendas.findBySlug(slug)
				.orElseThrow(() -> new NoEncontradoException("tienda")).getId();
		Pedido pedido = pedidos.findById(pedidoId)
				.orElseThrow(() -> new NoEncontradoException("pedido"));
		if (!pedido.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("pedido");
		}
		if (!pedido.getCliente().getUsuario().getId().equals(usuarioId)) {
			throw new ProhibidoException("no es tu pedido");
		}
		List<EnvioTracking> lista = envios.findByPedidoId(pedidoId).stream()
				.map(this::envioTracking)
				.toList();
		return new TrackingResponse(pedido.getNumeroPedido(), pedido.getEstado(), lista);
	}

	private EnvioTracking envioTracking(Envio envio) {
		List<EventoTracking> evs = eventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).stream()
				.map(e -> new EventoTracking(e.getEstado(), e.getDescripcion(), e.getUbicacion(), e.getOcurridoEn()))
				.toList();
		return new EnvioTracking(envio.getNumeroTracking(), envio.getEstado(), evs);
	}
}
