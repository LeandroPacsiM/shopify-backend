package com.tienda.plataforma.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarRolRequest(@NotBlank String rol) {
}
