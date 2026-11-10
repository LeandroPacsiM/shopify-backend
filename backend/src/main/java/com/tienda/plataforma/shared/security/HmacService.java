package com.tienda.plataforma.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * HMAC-SHA256 para códigos CUSTOMER (V21). El secreto vive en entorno
 * (`cliente.codigo.hmac-secret`, ≥32 caracteres) y se valida al arrancar
 * cuando el ámbito está habilitado. Comparación en tiempo constante.
 */
@Service
public class HmacService {

	private final String secreto;
	private final boolean habilitado;

	public HmacService(@Value("${cliente.codigo.hmac-secret:}") String secreto,
			@Value("${auth.customer.enabled:false}") boolean habilitado) {
		this.secreto = secreto == null ? "" : secreto;
		this.habilitado = habilitado;
	}

	@PostConstruct
	void validar() {
		if (habilitado && secreto.length() < 32) {
			throw new IllegalStateException(
					"cliente.codigo.hmac-secret debe tener al menos 32 caracteres");
		}
	}

	public String hmac(String texto) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("no se pudo calcular HMAC", e);
		}
	}

	public boolean coincide(String texto, String esperado) {
		if (esperado == null) {
			return false;
		}
		return MessageDigest.isEqual(hmac(texto).getBytes(StandardCharsets.UTF_8),
				esperado.getBytes(StandardCharsets.UTF_8));
	}
}
