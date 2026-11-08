package com.tienda.plataforma.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CrearReembolsoRequest(@NotNull @DecimalMin("0.01") BigDecimal monto, String motivo) {
}
