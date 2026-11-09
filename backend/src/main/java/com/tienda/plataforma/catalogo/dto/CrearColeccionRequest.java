package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearColeccionRequest(
		@NotBlank @Size(max = 255) String titulo,
		@NotBlank @Size(max = 255) String slug,
		String descripcion) {
}
