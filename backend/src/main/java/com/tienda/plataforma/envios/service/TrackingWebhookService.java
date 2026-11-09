package com.tienda.plataforma.envios.service;

import java.io.IOException;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.plataforma.envios.dto.TrackingWriteRequest;
import com.tienda.plataforma.envios.security.TrackingWebhookAuthenticator;
import com.tienda.plataforma.shared.exception.SolicitudInvalidaException;

import jakarta.validation.Validator;

/** Autentica el body crudo y recién después lo convierte en un evento de dominio. */
@Service
public class TrackingWebhookService {

	private final TrackingWebhookAuthenticator autenticador;
	private final ObjectMapper json = new ObjectMapper();
	private final Validator validador;
	private final EnvioService envios;

	public TrackingWebhookService(TrackingWebhookAuthenticator autenticador,
			Validator validador, EnvioService envios) {
		this.autenticador = autenticador;
		this.validador = validador;
		this.envios = envios;
	}

	public void ingresar(byte[] cuerpo, String timestamp, String firma) {
		autenticador.verificar(cuerpo, timestamp, firma);

		TrackingWriteRequest evento;
		try {
			JsonNode raiz = json.readTree(cuerpo);
			if (raiz == null || !raiz.isObject()) {
				throw new IllegalArgumentException("payload no es un objeto");
			}
			evento = new TrackingWriteRequest(texto(raiz, "numeroTracking"), texto(raiz, "estado"),
					texto(raiz, "descripcion"), texto(raiz, "ubicacion"), instante(raiz),
					texto(raiz, "referencia"));
		} catch (IOException | RuntimeException e) {
			throw new SolicitudInvalidaException("payload de tracking inválido");
		}
		if (evento == null || !validador.validate(evento).isEmpty()) {
			throw new SolicitudInvalidaException("payload de tracking inválido");
		}
		envios.tracking(evento);
	}

	private String texto(JsonNode raiz, String campo) {
		JsonNode valor = raiz.get(campo);
		if (valor == null || valor.isNull()) {
			return null;
		}
		if (!valor.isTextual()) {
			throw new IllegalArgumentException("campo textual inválido");
		}
		return valor.textValue();
	}

	private Instant instante(JsonNode raiz) {
		JsonNode valor = raiz.get("ocurridoEn");
		if (valor == null || valor.isNull()) {
			return null;
		}
		if (!valor.isTextual()) {
			throw new IllegalArgumentException("ocurridoEn inválido");
		}
		return Instant.parse(valor.textValue());
	}
}
