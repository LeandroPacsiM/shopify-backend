package com.tienda.plataforma.venta.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearDescuentoRequest(
		@NotBlank String codigo,
		@NotBlank String tipo,
		@NotNull @DecimalMin("0.00") BigDecimal valor,
		String descripcion,
		Integer usoMaximo,
		Instant iniciaEn,
		Instant terminaEn) {
}
