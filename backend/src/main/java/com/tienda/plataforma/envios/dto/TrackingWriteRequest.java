package com.tienda.plataforma.envios.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

public record TrackingWriteRequest(
		@NotBlank String numeroTracking,
		@NotBlank String estado,
		String descripcion,
		String ubicacion,
		Instant ocurridoEn,
		String referencia) {
}
