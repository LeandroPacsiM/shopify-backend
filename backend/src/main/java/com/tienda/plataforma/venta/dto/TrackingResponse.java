package com.tienda.plataforma.venta.dto;

import java.time.Instant;
import java.util.List;

public record TrackingResponse(
		String numeroPedido,
		String estadoPedido,
		List<EnvioTracking> envios) {

	public record EnvioTracking(
			String numeroTracking,
			String estado,
			List<EventoTracking> eventos) {
	}

	public record EventoTracking(
			String estado,
			String descripcion,
			String ubicacion,
			Instant ocurridoEn) {
	}
}
