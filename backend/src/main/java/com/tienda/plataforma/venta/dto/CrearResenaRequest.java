package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CrearResenaRequest(
		@NotNull Long productoId,
		@NotNull @Min(1) @Max(5) Integer calificacion,
		String titulo,
		String comentario) {
}
