package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearTiendaRequest(
		@NotBlank @Size(max = 255) String nombre,
		@NotBlank @Size(min = 3, max = 100)
		@Pattern(regexp = "[a-z0-9-]+") String slug,
		@Size(max = 255) String descripcion) {
}
