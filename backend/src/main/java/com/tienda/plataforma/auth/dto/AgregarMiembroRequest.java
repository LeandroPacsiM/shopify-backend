package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AgregarMiembroRequest(@NotBlank @Email String email, @NotBlank String rol) {
}
