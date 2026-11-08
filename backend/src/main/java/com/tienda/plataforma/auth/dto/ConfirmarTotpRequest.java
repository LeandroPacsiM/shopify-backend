package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmarTotpRequest(
		@Email @NotBlank String email,
		@NotBlank String password,
		@NotBlank @Pattern(regexp = "\\d{6}") String codigo) {
}
