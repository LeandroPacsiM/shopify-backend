package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearEtiquetaRequest(
		@NotBlank @Size(max = 100) String nombre,
		@NotBlank @Size(max = 100) String slug) {
}
