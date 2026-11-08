package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ConfirmarTotpRequest(
		@Email @NotBlank String email,
		@NotBlank String password,
		@Min(0) @Max(999999) int codigo) {
}
