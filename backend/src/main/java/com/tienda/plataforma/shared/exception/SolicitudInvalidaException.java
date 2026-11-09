package com.tienda.plataforma.shared.exception;

/** Payload HTTP inválido que no debe llegar a la lógica de negocio. */
public class SolicitudInvalidaException extends RuntimeException {

	public SolicitudInvalidaException(String mensaje) {
		super(mensaje);
	}
}
