package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.Size;

public record ActualizarPerfilRequest(
		@Size(max = 100) String nombre,
		@Size(max = 100) String apellido,
		@Size(max = 30) String telefono,
		Boolean aceptaMarketing) {
}
