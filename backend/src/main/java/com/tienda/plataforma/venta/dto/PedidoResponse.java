package com.tienda.plataforma.venta.dto;

import java.math.BigDecimal;

public record PedidoResponse(
		Long id,
		String numeroPedido,
		String estado,
		BigDecimal total,
		String emailContacto,
		String telefonoContacto,
		String notasCliente) {

	/** Compatibilidad con listados que no exponen contacto. */
	public PedidoResponse(Long id, String numeroPedido, String estado, BigDecimal total) {
		this(id, numeroPedido, estado, total, null, null, null);
	}
}
