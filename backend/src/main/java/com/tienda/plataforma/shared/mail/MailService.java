package com.tienda.plataforma.shared.mail;

import java.util.List;

/** Correo por API HTTPS (RNF-SEG-04). Implementación: Resend. */
public interface MailService {

	void enviar(String para, String asunto, String html);

	default void enviar(String para, String asunto, String html, List<String> copias) {
		enviar(para, asunto, html);
	}
}
