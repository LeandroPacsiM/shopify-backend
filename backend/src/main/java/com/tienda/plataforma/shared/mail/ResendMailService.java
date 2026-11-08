package com.tienda.plataforma.shared.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Resend por HTTPS. Sin llave configurada opera en modo log
 * (no rompe flujos; los correos quedan en el log para dev).
 */
@Service
public class ResendMailService implements MailService {

	private static final Logger LOG = LoggerFactory.getLogger(ResendMailService.class);

	private final String apiKey;
	private final String remitente;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public ResendMailService(
			@Value("${mail.resend.key:}") String apiKey,
			@Value("${mail.remitente:no-reply@example.com}") String remitente) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.remitente = remitente;
	}

	@Override
	public void enviar(String para, String asunto, String html) {
		if (apiKey.isEmpty()) {
			LOG.info("[mail:log] para={} asunto={} html={}", para, asunto, html);
			return;
		}
		try {
			String cuerpo = "{\"from\":\"" + escapar(remitente) + "\",\"to\":[\"" + escapar(para)
					+ "\"],\"subject\":\"" + escapar(asunto) + "\",\"html\":\"" + escapar(html) + "\"}";
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.resend.com/emails"))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "Bearer " + apiKey)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
					.build();
			HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
				throw new IllegalStateException("resend " + respuesta.statusCode() + ": " + respuesta.body());
			}
		} catch (Exception e) {
			throw new IllegalStateException("envío de correo", e);
		}
	}

	private String escapar(String texto) {
		return texto.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r");
	}
}
