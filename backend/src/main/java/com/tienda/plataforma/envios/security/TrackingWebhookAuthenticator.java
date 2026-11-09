package com.tienda.plataforma.envios.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;

/** Verifica HMAC-SHA256 del body exacto y limita el timestamp para frenar replay. */
@Component
public class TrackingWebhookAuthenticator {

	public static final String HEADER_TIMESTAMP = "X-Tracking-Timestamp";
	public static final String HEADER_SIGNATURE = "X-Tracking-Signature";

	private static final Duration VENTANA = Duration.ofMinutes(5);
	private static final int SECRETO_MINIMO_BYTES = 32;

	private final byte[] secreto;

	public TrackingWebhookAuthenticator(@Value("${tracking.webhook-secret:}") String secreto) {
		this.secreto = secreto == null ? new byte[0] : secreto.trim().getBytes(StandardCharsets.UTF_8);
	}

	/** Firma esperada: hex(HMAC-SHA256(secret, timestamp + "." + rawBodyUtf8)). */
	public void verificar(byte[] cuerpo, String timestamp, String firma) {
		if (secreto.length < SECRETO_MINIMO_BYTES || cuerpo == null
				|| timestamp == null || firma == null || firma.isBlank()) {
			throw firmaInvalida();
		}

		String timestampNormalizado = timestamp.trim();
		Instant firmadoEn;
		try {
			firmadoEn = Instant.ofEpochSecond(Long.parseLong(timestampNormalizado));
		} catch (RuntimeException e) {
			throw firmaInvalida();
		}
		Duration diferencia;
		try {
			diferencia = Duration.between(firmadoEn, Instant.now());
		} catch (ArithmeticException e) {
			throw firmaInvalida();
		}
		if (diferencia.compareTo(VENTANA) > 0 || diferencia.compareTo(VENTANA.negated()) < 0) {
			throw firmaInvalida();
		}

		byte[] recibida;
		try {
			recibida = HexFormat.of().parseHex(firma.trim());
		} catch (IllegalArgumentException e) {
			throw firmaInvalida();
		}
		if (!MessageDigest.isEqual(firmaEsperada(timestampNormalizado, cuerpo), recibida)) {
			throw firmaInvalida();
		}
	}

	private byte[] firmaEsperada(String timestamp, byte[] cuerpo) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
			mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
			return mac.doFinal(cuerpo);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 no disponible", e);
		}
	}

	private NoAutorizadoException firmaInvalida() {
		return new NoAutorizadoException("firma de tracking inválida");
	}
}
