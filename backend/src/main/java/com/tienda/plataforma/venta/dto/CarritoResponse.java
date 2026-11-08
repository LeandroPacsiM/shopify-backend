package com.tienda.plataforma.venta.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarritoResponse(
		Long id,
		String token,
		String estado,
		List<LineaResponse> lineas,
		BigDecimal subtotal) {

	public record LineaResponse(
			Long varianteId,
			String sku,
			Integer cantidad,
			BigDecimal precioUnitario,
			BigDecimal total) {
	}
}
