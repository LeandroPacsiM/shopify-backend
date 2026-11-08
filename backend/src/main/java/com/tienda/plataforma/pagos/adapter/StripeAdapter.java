package com.tienda.plataforma.pagos.adapter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.FirmaStripe;

/**
 * Stripe en modo test detrás de {@link PaymentAdapter} (RF-PAY-01).
 * Llama a la API real con la llave de LA TIENDA (descifrada en memoria,
 * jamás a logs); en modo vivo se niega (el live llega después).
 *
 * <p>Formato de {@code secretoCifrado} (descifrado): {@code sk_test_...}
 * o {@code sk_test_...;whsec_...} para verificar webhooks con el esquema
 * real de Stripe ({@code t=..,v1=..} HMAC-SHA256). Sin {@code whsec} la
 * firma se rechaza (falso por defecto seguro).
 */
@Component
public class StripeAdapter implements PaymentAdapter {

	private final CryptoService crypto;
	private final String baseUrl;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ObjectMapper json = new ObjectMapper();

	public StripeAdapter(CryptoService crypto,
			@Value("${stripe.api-base:https://api.stripe.com}") String baseUrl) {
		this.crypto = crypto;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	@Override
	public String codigo() {
		return "stripe";
	}

	@Override
	public CargoResultado autorizar(TiendaPasarela credenciales, BigDecimal monto, String moneda,
			String idempotencia) {
		exigirTest(credenciales);
		String cuerpo = "amount=" + centimos(monto)
				+ "&currency=" + URLEncoder.encode(moneda.toLowerCase(), StandardCharsets.UTF_8)
				+ "&automatic_payment_methods[enabled]=true"
				+ "&automatic_payment_methods[allow_redirects]=never";
		String respuesta = post("/v1/payment_intents", skDe(credenciales), idempotencia, cuerpo);
		return new CargoResultado(idDe(respuesta, "payment_intent"), Pago.PROCESANDO, "stripe");
	}

	@Override
	public ReembolsoResultado reembolsar(TiendaPasarela credenciales, String transaccionId, BigDecimal monto) {
		exigirTest(credenciales);
		String cuerpo = "payment_intent=" + URLEncoder.encode(transaccionId, StandardCharsets.UTF_8)
				+ "&amount=" + centimos(monto);
		String respuesta = post("/v1/refunds", skDe(credenciales), null, cuerpo);
		return new ReembolsoResultado(idDe(respuesta, "refund"), Reembolso.PROCESANDO);
	}

	/**
	 * Esquema real de Stripe con el {@code whsec} de la tienda
	 * (segunda parte de {@code secretoCifrado}). Sin {@code whsec},
	 * falso por defecto seguro.
	 */
	@Override
	public boolean verificaFirma(TiendaPasarela credenciales, String cuerpo, String firma) {
		return FirmaStripe.verificar(whsecDe(credenciales), cuerpo, firma);
	}

	private void exigirTest(TiendaPasarela credenciales) {
		if (!TiendaPasarela.TEST.equals(credenciales.getModo())) {
			throw new IllegalStateException("stripe test solo en modo test");
		}
	}

	private String skDe(TiendaPasarela credenciales) {
		String plano = crypto.descifrar(credenciales.getSecretoCifrado());
		int corte = plano.indexOf(';');
		return corte < 0 ? plano : plano.substring(0, corte);
	}

	private String whsecDe(TiendaPasarela credenciales) {
		try {
			String plano = crypto.descifrar(credenciales.getSecretoCifrado());
			int corte = plano.indexOf(';');
			return corte < 0 ? "" : plano.substring(corte + 1);
		} catch (Exception e) {
			return "";
		}
	}

	private long centimos(BigDecimal monto) {
		return monto.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	private String post(String ruta, String secreto, String idempotencia, String cuerpo) {
		try {
			HttpRequest.Builder solicitud = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + ruta))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "Bearer " + secreto)
					.header("Content-Type", "application/x-www-form-urlencoded");
			if (idempotencia != null) {
				solicitud.header("Idempotency-Key", idempotencia);
			}
			HttpResponse<String> respuesta = http.send(
					solicitud.POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
					HttpResponse.BodyHandlers.ofString());
			if (respuesta.statusCode() < 200 || respuesta.statusCode() >= 300) {
				throw new IllegalStateException("stripe " + respuesta.statusCode() + ": " + respuesta.body());
			}
			return respuesta.body();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe no disponible", e);
		}
	}

	private String idDe(String respuesta, String que) {
		try {
			JsonNode id = json.readTree(respuesta).get("id");
			if (id == null || !id.isTextual()) {
				throw new IllegalStateException("stripe sin id en " + que);
			}
			return id.asText();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("stripe respuesta inválida", e);
		}
	}
}
