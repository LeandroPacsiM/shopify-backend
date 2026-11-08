package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearProductoRequest(
		@NotBlank String titulo,
		@NotBlank String slug,
		String descripcion) {
}
