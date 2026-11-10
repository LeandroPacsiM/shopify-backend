package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroAdministrativoRequest(
		@Email @NotBlank String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@Size(max = 100) String nombre,
		@Size(max = 100) String apellido) {
}
