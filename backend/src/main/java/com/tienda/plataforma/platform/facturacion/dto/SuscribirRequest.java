package com.tienda.plataforma.platform.facturacion.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

public record SuscribirRequest(@NotBlank String plan) {
}
