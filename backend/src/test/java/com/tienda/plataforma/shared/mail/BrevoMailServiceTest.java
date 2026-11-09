package com.tienda.plataforma.shared.mail;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Brevo: forma del request real (stub local) + modo log sin llave. */
class BrevoMailServiceTest {

	private HttpServer stub;
	private final AtomicReference<String> ultimoAuth = new AtomicReference<>();
	private final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();

	@BeforeEach
	void arrancar() throws Exception {
		stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		stub.createContext("/v3/smtp/email", intercambio -> {
			ultimoAuth.set(intercambio.getRequestHeaders().getFirst("api-key"));
			ultimoCuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] json = "{\"messageId\":\"<test@relay>\"}".getBytes(StandardCharsets.UTF_8);
			intercambio.getResponseHeaders().add("Content-Type", "application/json");
			intercambio.sendResponseHeaders(201, json.length);
			intercambio.getResponseBody().write(json);
			intercambio.close();
		});
		stub.start();
	}

	@AfterEach
	void detener() {
		stub.stop(0);
	}

	@Test
	void enviaConFormaBrevo() {
		var servicio = new BrevoMailService("clave-brevo", "tienda@example.com",
				"http://127.0.0.1:" + stub.getAddress().getPort(), true);
		assertDoesNotThrow(() -> servicio.enviar("dest@example.com", "Asunto", "<p>hola</p>"));

		assertEquals("clave-brevo", ultimoAuth.get());
		assertTrue(ultimoCuerpo.get().contains("\"sender\":{\"email\":\"tienda@example.com\"}"), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("\"to\":[{\"email\":\"dest@example.com\"}]"), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("\"subject\":\"Asunto\""), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("\"htmlContent\":\"<p>hola</p>\""), ultimoCuerpo.get());
	}

	@Test
	void sinLlaveModoLog() {
		var servicio = new BrevoMailService("", "tienda@example.com",
				"http://127.0.0.1:" + stub.getAddress().getPort(), true);
		assertDoesNotThrow(() -> servicio.enviar("dest@example.com", "Asunto", "<p>hola</p>"));
		assertNull(ultimoAuth.get());
	}

	@Test
	void sinLlaveYLogProhibidoFallaCerrado() {
		var servicio = new BrevoMailService("", "tienda@example.com",
				"http://127.0.0.1:" + stub.getAddress().getPort(), false);
		assertThrows(IllegalStateException.class,
				() -> servicio.enviar("dest@example.com", "Asunto", "<p>codigo-secreto</p>"));
		assertNull(ultimoAuth.get());
	}
}
