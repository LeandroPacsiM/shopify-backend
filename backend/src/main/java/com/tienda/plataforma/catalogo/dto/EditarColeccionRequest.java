package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.Size;

public record EditarColeccionRequest(
		@Size(max = 255) String titulo,
		String descripcion,
		Boolean activo) {
}
