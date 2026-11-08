package com.tienda.plataforma.pagos.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearIntentoRequest(@NotBlank String pasarela, @NotBlank String idempotencia) {
}
