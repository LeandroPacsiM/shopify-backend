package com.tienda.plataforma.venta.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CheckoutResponse(
		Long id,
		String estado,
		BigDecimal subtotal,
		BigDecimal descuentoTotal,
		BigDecimal impuestoTotal,
		BigDecimal envioTotal,
		BigDecimal total,
		Instant expiraEn,
		String emailContacto,
		String telefonoContacto,
		String notasCliente) {
}
