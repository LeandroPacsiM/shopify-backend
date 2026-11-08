package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.tienda.plataforma.pagos.adapter.PaymentAdapter;
import com.tienda.plataforma.pagos.adapter.StripeAdapter;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.shared.security.CryptoService;

/**
 * Verificación EN VIVO contra api.stripe.com (temporal, no es parte de la
 * suite). Lee la llave SOLO del entorno; sin ella se omite. La llave jamás
 * queda en el repo: se pasa por variable de sesión y se rota después.
 */
class StripeVivoIT {

	private static final String MASTER_DEV = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Test
	void crearYCancelarPaymentIntent() throws Exception {
		String sk = System.getenv("STRIPE_API_KEY");
		assumeTrue(sk != null && (sk.startsWith("sk_test_") || sk.startsWith("rk_test_")),
				"sin STRIPE_API_KEY de prueba; se omite");
		CryptoService crypto = new CryptoService(MASTER_DEV);
		StripeAdapter adapter = new StripeAdapter(crypto, "https://api.stripe.com");

		TiendaPasarela creds = new TiendaPasarela();
		creds.setClavePublica("pk_test_sandbox");
		creds.setSecretoCifrado(crypto.cifrar(sk));
		creds.setModo(TiendaPasarela.TEST);
		creds.setActivo(true);

		PaymentAdapter.CargoResultado cargo =
				adapter.autorizar(creds, new BigDecimal("10.00"), "PEN", "vivo-it-1");
		assertTrue(cargo.transaccionExternaId().startsWith("pi_"),
				cargo.transaccionExternaId());

		cancelar(sk, cargo.transaccionExternaId());
	}

	private void cancelar(String sk, String pi) throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HttpResponse<String> r = http.send(
				HttpRequest.newBuilder()
						.uri(URI.create("https://api.stripe.com/v1/payment_intents/" + pi + "/cancel"))
						.timeout(Duration.ofSeconds(15))
						.header("Authorization", "Bearer " + sk)
						.POST(HttpRequest.BodyPublishers.noBody())
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertTrue(r.statusCode() >= 200 && r.statusCode() < 300, r.body());
		assertTrue(r.body().contains("\"canceled\""), r.body());
	}
}
