package com.tienda.plataforma.pagos.dto;

import jakarta.validation.constraints.NotBlank;

public record WebhookPagoRequest(
		@NotBlank String eventId,
		@NotBlank String transaccionId,
		@NotBlank String estado,
		String firma,
		String cuerpo) {
}
