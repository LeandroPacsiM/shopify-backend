package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CrearCheckoutRequest(
		String carritoToken,
		Long direccionEnvioId,
		Long direccionFacturacionId,
		Long metodoEnvioId,
		String codigoDescuento,
		@Email @Size(max = 255) String emailContacto,
		@Size(max = 50) String telefonoContacto,
		@Size(max = 1000) String notasCliente) {

	/** Compatibilidad con llamadas que no mandan contacto. */
	public CrearCheckoutRequest(String carritoToken, Long direccionEnvioId,
			Long direccionFacturacionId, Long metodoEnvioId, String codigoDescuento) {
		this(carritoToken, direccionEnvioId, direccionFacturacionId, metodoEnvioId,
				codigoDescuento, null, null, null);
	}
}
