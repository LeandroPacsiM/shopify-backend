package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearDireccionRequest(
		@NotBlank String linea1,
		String linea2,
		String ciudad,
		String departamento,
		String codigoPostal,
		String pais,
		boolean esPrincipal) {

	public record DireccionResponse(
			Long id,
			String linea1,
			String ciudad,
			String pais,
			boolean esPrincipal) {
	}
}
