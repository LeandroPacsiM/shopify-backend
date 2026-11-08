package com.tienda.plataforma.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoEncontradoException extends RuntimeException {

	public NoEncontradoException(String recurso) {
		super(recurso + " no encontrado");
	}
}
