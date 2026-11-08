package com.tienda.plataforma.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class NoAutorizadoException extends RuntimeException {

	public NoAutorizadoException(String mensaje) {
		super(mensaje);
	}
}
