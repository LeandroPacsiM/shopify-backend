package com.tienda.plataforma.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearReembolsoRequest(@NotNull @DecimalMin("0.01") BigDecimal monto,
		@Size(max = 255) String motivo) {
}
