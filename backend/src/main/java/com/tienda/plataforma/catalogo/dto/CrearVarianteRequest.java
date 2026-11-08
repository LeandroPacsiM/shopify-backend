package com.tienda.plataforma.catalogo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CrearVarianteRequest(
		String sku,
		@NotNull @DecimalMin("0.00") BigDecimal precio,
		BigDecimal precioComparativo,
		Integer pesoGramos) {
}
