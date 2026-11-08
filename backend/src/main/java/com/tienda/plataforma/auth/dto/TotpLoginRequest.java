package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TotpLoginRequest(
		@Email @NotBlank String email,
		@NotBlank String password,
		Integer codigo,
		String backupCode) {
}
