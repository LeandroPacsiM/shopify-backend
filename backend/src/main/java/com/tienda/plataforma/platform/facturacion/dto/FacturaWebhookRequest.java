package com.tienda.plataforma.platform.facturacion.dto;

import jakarta.validation.constraints.NotBlank;

public record FacturaWebhookRequest(
		@NotBlank String eventId,
		@NotBlank String suscripcionId,
		@NotBlank String tipo,
		String firma,
		String cuerpo) {
}
