package com.tienda.plataforma.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarPasarelaRequest(
		@NotBlank @Size(max = 50) String codigo,
		@NotBlank @Size(max = 255) String clavePublica,
		@NotBlank @Size(max = 500) String secreto,
		@NotBlank String modo) {
}
