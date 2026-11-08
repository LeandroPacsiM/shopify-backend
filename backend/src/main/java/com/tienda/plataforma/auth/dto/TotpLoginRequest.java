package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpLoginRequest(
		@Email @NotBlank String email,
		@NotBlank String password,
		@Pattern(regexp = "\\d{6}") String codigo,
		String backupCode) {
}
