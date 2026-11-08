package com.tienda.plataforma.shared.mail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class MailServiceTest {

	@Autowired
	MailService mailService;

	@Test
	void sinLlaveOperaEnModoLog() {
		assertDoesNotThrow(() -> mailService.enviar("test@example.com", "Prueba", "<p>hola</p>"));
	}
}
