package com.tienda.plataforma.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearUbicacionRequest(
		@NotBlank @Size(max = 255) String nombre,
		@Size(max = 255) String direccion,
		@Size(max = 100) String ciudad,
		@Size(max = 100) String pais) {
}
