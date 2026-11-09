package com.tienda.plataforma.envios.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackingWriteRequest(
		@NotBlank @Size(max = 100) String numeroTracking,
		@NotBlank @Size(max = 50) String estado,
		@Size(max = 255) String descripcion,
		@Size(max = 255) String ubicacion,
		Instant ocurridoEn,
		@NotBlank @Size(max = 255) String referencia) {
}
