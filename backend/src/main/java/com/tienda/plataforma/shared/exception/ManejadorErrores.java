package com.tienda.plataforma.shared.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores uniformes {codigo, mensaje}. Sin stacktraces al cliente. */
@RestControllerAdvice
public class ManejadorErrores {

	@ExceptionHandler(ConflictoException.class)
	public ResponseEntity<Map<String, Object>> conflicto(ConflictoException e) {
		return cuerpo(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(NoAutorizadoException.class)
	public ResponseEntity<Map<String, Object>> noAutorizado(NoAutorizadoException e) {
		return cuerpo(HttpStatus.UNAUTHORIZED, e.getMessage());
	}

	@ExceptionHandler(NoEncontradoException.class)
	public ResponseEntity<Map<String, Object>> noEncontrado(NoEncontradoException e) {
		return cuerpo(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ProhibidoException.class)
	public ResponseEntity<Map<String, Object>> prohibido(ProhibidoException e) {
		return cuerpo(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(SolicitudInvalidaException.class)
	public ResponseEntity<Map<String, Object>> solicitudInvalida(SolicitudInvalidaException e) {
		return cuerpo(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	private ResponseEntity<Map<String, Object>> cuerpo(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado)
				.body(Map.of("codigo", estado.value(), "mensaje", mensaje, "cuando", Instant.now().toString()));
	}
}
