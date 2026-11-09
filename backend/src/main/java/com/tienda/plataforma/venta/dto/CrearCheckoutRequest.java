package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CrearCheckoutRequest(
		String carritoToken,
		Long direccionEnvioId,
		Long direccionFacturacionId,
		Long metodoEnvioId,
		String codigoDescuento,
		java.util.List<String> codigosDescuento,
		@Email @Size(max = 255) String emailContacto,
		@Size(max = 50) String telefonoContacto,
		@Size(max = 1000) String notasCliente) {

	/** Compatibilidad con llamadas que no mandan contacto. */
	public CrearCheckoutRequest(String carritoToken, Long direccionEnvioId,
			Long direccionFacturacionId, Long metodoEnvioId, String codigoDescuento) {
		this(carritoToken, direccionEnvioId, direccionFacturacionId, metodoEnvioId,
				codigoDescuento, null, null, null, null);
	}

	/** Compatibilidad con llamadas de un solo código más contacto. */
	public CrearCheckoutRequest(String carritoToken, Long direccionEnvioId,
			Long direccionFacturacionId, Long metodoEnvioId, String codigoDescuento,
			String emailContacto, String telefonoContacto, String notasCliente) {
		this(carritoToken, direccionEnvioId, direccionFacturacionId, metodoEnvioId,
				codigoDescuento, null, emailContacto, telefonoContacto, notasCliente);
	}
}
