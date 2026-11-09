package com.tienda.plataforma.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MovimientoStockRequest(
		@NotNull Long varianteId,
		@NotNull Long ubicacionId,
		@NotBlank String tipo,
		@NotNull Integer cantidad,
		@Size(max = 255) String motivo) {
}
