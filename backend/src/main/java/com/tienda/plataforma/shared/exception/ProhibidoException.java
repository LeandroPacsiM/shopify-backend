package com.tienda.plataforma.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProhibidoException extends RuntimeException {

	public ProhibidoException(String mensaje) {
		super(mensaje);
	}
}
