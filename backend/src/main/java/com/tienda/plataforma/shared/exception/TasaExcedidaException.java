package com.tienda.plataforma.shared.exception;

/** 429: rate limiting de códigos CUSTOMER (por tienda/email/IP). */
public class TasaExcedidaException extends RuntimeException {

	public TasaExcedidaException(String mensaje) {
		super(mensaje);
	}
}
