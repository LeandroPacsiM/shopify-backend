package com.tienda.plataforma.realtime.dto;

import java.time.Instant;
import java.util.Map;

public record NotificacionResponse(
		Long id,
		String tipo,
		String entidad,
		Long entidadId,
		Map<String, Object> datos,
		Instant leidaEn,
		Instant creadoEn) {
}
