package com.tienda.plataforma.platform.facturacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * Verificación EN VIVO del cobro de plataforma (temporal). Lee llaves SOLO
 * del entorno; sin ellas se omite. Cancela la suscripción creada para dejar
 * limpio el sandbox. La suscripción queda pendiente hasta el webhook real.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class StripeFacturacionVivoIT {

	@Autowired
	FacturacionService facturacion;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	SuscripcionRepository suscripciones;

	@Test
	void suscribirMensualEnVivo() throws Exception {
		String sk = System.getenv("STRIPE_PLATFORM_KEY");
		assumeTrue(sk != null && (sk.startsWith("sk_test_") || sk.startsWith("rk_test_")),
				"sin STRIPE_PLATFORM_KEY de prueba; se omite");
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("FV-" + tag);
		tienda.setSlug("fv-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		var resp = facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);
		assertEquals(Suscripcion.PENDIENTE, resp.estado());
		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertTrue(s.getStripeCustomerId().startsWith("cus_"), s.getStripeCustomerId());
		assertTrue(s.getStripeSubscriptionId().startsWith("sub_"), s.getStripeSubscriptionId());

		cancelar(sk, s.getStripeSubscriptionId());
	}

	private void cancelar(String sk, String sub) throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		HttpResponse<String> r = http.send(
				HttpRequest.newBuilder()
						.uri(URI.create("https://api.stripe.com/v1/subscriptions/" + sub))
						.timeout(Duration.ofSeconds(15))
						.header("Authorization", "Bearer " + sk)
						.DELETE()
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertTrue(r.statusCode() >= 200 && r.statusCode() < 300, r.body());
	}
}
