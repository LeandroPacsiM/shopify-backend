package com.tienda.plataforma.platform.facturacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sun.net.httpserver.HttpServer;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionEventoRepository;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** Stripe en modo test (stub local) + eventos crudos. Sin llaves reales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class FacturacionStripeTest {

	static final String WHSEC = "whsec_test_v19";
	static final HttpServer stub;
	static final AtomicReference<String> ultimoAuth = new AtomicReference<>();
	static final AtomicReference<String> ultimoCuerpoSub = new AtomicReference<>();
	static final AtomicReference<String> ultimaIdempotenciaSub = new AtomicReference<>();
	static final AtomicReference<String> ultimaIdempotenciaCustomer = new AtomicReference<>();
	static final AtomicReference<String> ultimoMetodoSub = new AtomicReference<>();
	static final AtomicReference<String> ultimaRutaDelete = new AtomicReference<>();

	static {
		try {
			stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			stub.createContext("/v1/customers", x -> {
				ultimaIdempotenciaCustomer.set(x.getRequestHeaders().getFirst("Idempotency-Key"));
				responder(x, "{\"id\":\"cus_test_1\"}");
			});
			stub.createContext("/v1/subscriptions", x -> {
				ultimoMetodoSub.set(x.getRequestMethod());
				ultimaIdempotenciaSub.set(x.getRequestHeaders().getFirst("Idempotency-Key"));
				if ("DELETE".equalsIgnoreCase(x.getRequestMethod())) {
					ultimaRutaDelete.set(x.getRequestURI().getPath());
					responder(x, "{\"id\":\"sub_test_9\",\"status\":\"canceled\"}");
					return;
				}
				if ("GET".equalsIgnoreCase(x.getRequestMethod())) {
					responder(x, "{\"id\":\"sub_test_9\",\"latest_invoice\":"
							+ "{\"payment_intent\":{\"client_secret\":\"pi_secret_test\"}}}");
					return;
				}
				ultimoCuerpoSub.set(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				responder(x, "{\"id\":\"sub_test_9\",\"latest_invoice\":"
						+ "{\"payment_intent\":{\"client_secret\":\"pi_secret_test\"}}}");
			});
			stub.createContext("/v1/prices", x -> {
				String intervalo = x.getRequestURI().getPath().contains("price_a") ? "year" : "month";
				responder(x, "{\"id\":\"price_x\",\"recurring\":{\"interval\":\"" + intervalo + "\"}}");
			});
			stub.start();
		} catch (Exception e) {
			throw new IllegalStateException("stub", e);
		}
	}

	@AfterAll
	static void detener() {
		stub.stop(0);
	}

	@DynamicPropertySource
	static void stripe(DynamicPropertyRegistry r) {
		r.add("stripe.api-base", () -> "http://127.0.0.1:" + stub.getAddress().getPort());
		r.add("stripe.platform-key", () -> "sk_test_x");
		r.add("stripe.price-mensual", () -> "price_m");
		r.add("stripe.price-anual", () -> "price_a");
		r.add("stripe.platform-whsec", () -> WHSEC);
	}

	static void responder(com.sun.net.httpserver.HttpExchange x, String json) {
		try {
			ultimoAuth.set(x.getRequestHeaders().getFirst("Authorization"));
			x.getRequestBody().readAllBytes();
			byte[] b = json.getBytes(StandardCharsets.UTF_8);
			x.getResponseHeaders().add("Content-Type", "application/json");
			x.sendResponseHeaders(200, b.length);
			x.getResponseBody().write(b);
			x.close();
		} catch (Exception e) {
			throw new IllegalStateException("stub", e);
		}
	}

	@Autowired
	MockMvc mvc;
	@Autowired
	FacturacionService facturacion;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	SuscripcionRepository suscripciones;

	@Test
	void suscribirCreaEnStripe() {
		Tienda tienda = nuevaTienda("fm-");
		var resp = facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);
		assertEquals(Suscripcion.PENDIENTE, resp.estado());

		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertEquals("cus_test_1", s.getStripeCustomerId());
		assertEquals("sub_test_9", s.getStripeSubscriptionId());
		assertEquals("Bearer sk_test_x", ultimoAuth.get());
		assertTrue(ultimoCuerpoSub.get().contains("price_m"), ultimoCuerpoSub.get());

		Tienda anual = nuevaTienda("fa-");
		facturacion.suscribir(anual.getId(), Suscripcion.ANUAL);
		assertTrue(ultimoCuerpoSub.get().contains("price_a"), ultimoCuerpoSub.get());
	}

	@Test
	void suscribirEnviaIdempotenciaEstable() {
		Tienda tienda = nuevaTienda("fi-");
		facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);

		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertEquals("facturacion-" + s.getId() + "-customer", ultimaIdempotenciaCustomer.get());
		assertEquals("facturacion-" + s.getId() + "-mensual-subscription", ultimaIdempotenciaSub.get());
		assertTrue(ultimoCuerpoSub.get().contains("expand"), ultimoCuerpoSub.get());
	}

	@Test
	void suscribirDevuelveClientSecretYVerLoRecupera() {
		Tienda tienda = nuevaTienda("fs-");
		var resp = facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);

		assertEquals("pi_secret_test", resp.clientSecret());
		assertEquals("pi_secret_test", facturacion.ver(tienda.getId()).clientSecret());
	}

	@Test
	void cancelarTambienCancelaEnStripe() {
		Tienda tienda = nuevaTienda("fx-");
		facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);

		var resp = facturacion.cancelar(tienda.getId());

		assertEquals(Suscripcion.CANCELADA, resp.estado());
		assertEquals("DELETE", ultimoMetodoSub.get());
		assertEquals("/v1/subscriptions/sub_test_9", ultimaRutaDelete.get());
		assertEquals(Suscripcion.CANCELADA,
				suscripciones.findByTiendaId(tienda.getId()).orElseThrow().getEstado());
	}

	@Test
	void cancelarSinStripeLocalNoLlamaAfuera() {
		Tienda tienda = nuevaTienda("fl-");
		Suscripcion s = new Suscripcion();
		s.setTienda(tienda);
		s.setPlan(Suscripcion.MENSUAL);
		s.setEstado(Suscripcion.PENDIENTE);
		suscripciones.save(s);
		ultimoMetodoSub.set(null);

		var resp = facturacion.cancelar(tienda.getId());

		assertEquals(Suscripcion.CANCELADA, resp.estado());
		assertNull(ultimoMetodoSub.get());
	}

	@Test
	void constructorRechazaLlavePlataformaLive() {
		assertThrows(IllegalStateException.class, () -> new FacturacionService(
				mock(SuscripcionRepository.class),
				mock(SuscripcionEventoRepository.class),
				mock(TiendaRepository.class), WHSEC, "sk_live_no_usar", "price_m", "price_a",
				"http://127.0.0.1:1"));
	}

	@Test
	void invoiceRenuevaYDeletedCancela() throws Exception {
		Tienda tienda = nuevaTienda("fw-");
		facturacion.suscribir(tienda.getId(), Suscripcion.MENSUAL);

		String inv = "{\"id\":\"evt-w-1\",\"type\":\"invoice.payment_succeeded\","
				+ "\"data\":{\"object\":{\"subscription\":\"sub_test_9\"}}}";
		enviar(inv).andExpect(status().isOk());
		enviar(inv).andExpect(status().isOk());
		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertEquals(Suscripcion.ACTIVA, s.getEstado());
		assertTrue(s.getPeriodoFin().isAfter(Instant.now().plusSeconds(29 * 24 * 60 * 60)));
		assertTrue(tiendas.findById(tienda.getId()).orElseThrow().getActiva());

		String del = "{\"id\":\"evt-w-2\",\"type\":\"customer.subscription.deleted\","
				+ "\"data\":{\"object\":{\"id\":\"sub_test_9\"}}}";
		enviar(del).andExpect(status().isOk());
		assertEquals(Suscripcion.CANCELADA,
				suscripciones.findByTiendaId(tienda.getId()).orElseThrow().getEstado());
		assertFalse(tiendas.findById(tienda.getId()).orElseThrow().getActiva());
	}

	private org.springframework.test.web.servlet.ResultActions enviar(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WHSEC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String v1 = HexFormat.of().formatHex(
				mac.doFinal(("1." + cuerpo).getBytes(StandardCharsets.UTF_8)));
		return mvc.perform(post("/api/webhooks/facturacion/stripe")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", "t=1,v1=" + v1)
				.content(cuerpo));
	}

	private Tienda nuevaTienda(String prefijo) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda t = new Tienda();
		t.setNombre(prefijo + tag);
		t.setSlug(prefijo + tag);
		t.setActiva(true);
		return tiendas.save(t);
	}
}
