package com.tienda.plataforma.envios.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;

class TrackingWebhookAuthenticatorTest {

	private static final String SECRET = "tracking-webhook-test-secret-minimum-32-bytes";

	@Test
	void aceptaFirmaVigenteDelBodyExacto() throws Exception {
		byte[] body = "{\"referencia\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
		String timestamp = Long.toString(Instant.now().getEpochSecond());

		assertDoesNotThrow(() -> new TrackingWebhookAuthenticator(SECRET)
				.verificar(body, timestamp, firmar(body, timestamp)));
	}

	@Test
	void rechazaBodyAlteradoAunqueLaFirmaOriginalEraValida() throws Exception {
		String timestamp = Long.toString(Instant.now().getEpochSecond());
		byte[] bodyFirmado = "{\"referencia\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
		byte[] bodyRecibido = "{\"referencia\":\"evt-2\"}".getBytes(StandardCharsets.UTF_8);
		String firma = firmar(bodyFirmado, timestamp);

		assertThrows(NoAutorizadoException.class,
				() -> new TrackingWebhookAuthenticator(SECRET).verificar(bodyRecibido, timestamp, firma));
	}

	@Test
	void rechazaTimestampFueraDeVentana() throws Exception {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		String timestamp = Long.toString(Instant.now().minusSeconds(10 * 60).getEpochSecond());

		assertThrows(NoAutorizadoException.class,
				() -> new TrackingWebhookAuthenticator(SECRET).verificar(body, timestamp, firmar(body, timestamp)));
	}

	@Test
	void fallaCerradoSiFaltaUnSecretoSuficientementeLargo() {
		String timestamp = Long.toString(Instant.now().getEpochSecond());
		assertThrows(NoAutorizadoException.class,
				() -> new TrackingWebhookAuthenticator("").verificar(new byte[0], timestamp, "00"));
		assertThrows(NoAutorizadoException.class,
				() -> new TrackingWebhookAuthenticator("corto").verificar(new byte[0], timestamp, "00"));
	}

	private String firmar(byte[] body, String timestamp) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(mac.doFinal(body));
	}
}
