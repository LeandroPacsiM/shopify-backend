package com.tienda.plataforma.shared.mail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Resend: modo log sin contenido y falla cerrada sin llave en prod (AUTH-04). */
class ResendMailServiceTest {

	@Test
	void sinLlaveModoLogNoLanza() {
		var servicio = new ResendMailService("", "tienda@example.com", true);
		assertDoesNotThrow(
				() -> servicio.enviar("dest@example.com", "Asunto", "<p>codigo-secreto</p>"));
	}

	@Test
	void sinLlaveYLogProhibidoFallaCerrado() {
		var servicio = new ResendMailService("", "tienda@example.com", false);
		assertThrows(IllegalStateException.class,
				() -> servicio.enviar("dest@example.com", "Asunto", "<p>codigo-secreto</p>"));
	}
}
