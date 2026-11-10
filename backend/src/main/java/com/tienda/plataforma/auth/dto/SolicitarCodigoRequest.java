package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SolicitarCodigoRequest(
		@NotBlank String tiendaSlug,
		@Email @NotBlank String email) {
}
