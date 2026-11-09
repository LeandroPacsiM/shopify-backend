package com.tienda.plataforma.shared.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Brevo por HTTPS (`POST /v3/smtp/email`). Se elige con
 * `mail.proveedor=brevo`; sin llave opera en modo log solo cuando
 * `mail.log-permitido=true` (dev/test). En prod falla cerrado y el log
 * nunca incluye el contenido (lleva códigos de un solo uso).
 * El remitente debe estar verificado en Brevo (un sender propio envía
 * a cualquier destinatario en plan gratis).
 */
@Service
@ConditionalOnProperty(name = "mail.proveedor", havingValue = "brevo")
public class BrevoMailService implements MailService {

	private static final Logger LOG = LoggerFactory.getLogger(BrevoMailService.class);

	private final String apiKey;
	private final String remitente;
	private final String baseUrl;
	private final boolean logPermitido;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	public BrevoMailService(
			@Value("${mail.brevo.key:}") String apiKey,
			@Value("${mail.remitente:no-reply@example.com}") String remitente,
			@Value("${mail.brevo.api-base:https://api.brevo.com}") String baseUrl,
			@Value("${mail.log-permitido:true}") boolean logPermitido) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.remitente = remitente;
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.baseUrl = base;
		this.logPermitido = logPermitido;
	}

	@Override
	public void enviar(String para, String asunto, String html) {
		if (apiKey.isEmpty()) {
			if (!logPermitido) {
				throw new IllegalStateException("correo sin proveedor configurado");
			}
			LOG.info("[mail:log] para={} asunto={} htmlChars={}", para, asunto,
					html == null ? 0 : html.length());
			return;
		}
		try {
			String cuerpo = "{\"sender\":{\"email\":\"" + escapar(remitente) + "\"},"
					+ "\"to\":[{\"email\":\"" + escapar(para) + "\"}],"
					+ "\"subject\":\"" + escapar(asunto) + "\","
					+ "\"htmlContent\":\"" + escapar(html) + "\"}";
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/v3/smtp/email"))
					.timeout(Duration.ofSeconds(15))
					.header("api-key", apiKey)
					.header("accept", "application/json")
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
					.build();
			HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
				throw new IllegalStateException("brevo " + respuesta.statusCode() + ": " + respuesta.body());
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
