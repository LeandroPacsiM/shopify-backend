package com.tienda.plataforma.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearIntentoRequest(@NotBlank String pasarela,
		@NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9._~-]{1,200}") String idempotencia) {
}
