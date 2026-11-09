package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.tienda.plataforma.pagos.adapter.PaymentAdapter;
import com.tienda.plataforma.pagos.adapter.StripeAdapter;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.shared.security.CryptoService;

/** RF-PAY-01: Stripe test detrás del adapter (stub HTTP local, sin llaves reales). */
class StripeAdapterTest {

	/** Misma llave solo-dev de application-local.properties. */
	private static final String MASTER_DEV = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	private HttpServer stub;
	private final AtomicReference<String> ultimoAuth = new AtomicReference<>();
	private final AtomicReference<String> ultimaIdempotencia = new AtomicReference<>();
	private final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();
	private final AtomicReference<String> ultimaRuta = new AtomicReference<>();

	private StripeAdapter adapter;
	private CryptoService crypto;

	@BeforeEach
	void arrancar() throws Exception {
		crypto = new CryptoService(MASTER_DEV);
		stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		stub.createContext("/", intercambio -> {
			ultimoAuth.set(intercambio.getRequestHeaders().getFirst("Authorization"));
			ultimaIdempotencia.set(intercambio.getRequestHeaders().getFirst("Idempotency-Key"));
			ultimaRuta.set(intercambio.getRequestURI().getPath());
			ultimoCuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String id = ultimaRuta.get().contains("refunds") ? "re_test_1" : "pi_test_1";
			byte[] json = ("{\"id\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8);
			intercambio.getResponseHeaders().add("Content-Type", "application/json");
			intercambio.sendResponseHeaders(200, json.length);
			intercambio.getResponseBody().write(json);
			intercambio.close();
		});
		stub.start();
		adapter = new StripeAdapter(crypto,
				"http://127.0.0.1:" + stub.getAddress().getPort());
	}

	@AfterEach
	void detener() {
		stub.stop(0);
	}

	@Test
	void autorizarCreaPaymentIntent() {
		TiendaPasarela creds = creds("sk_test_abc", TiendaPasarela.TEST);
		PaymentAdapter.CargoResultado cargo =
				adapter.autorizar(creds, new BigDecimal("114.40"), "PEN", "idem-1");

		assertEquals("pi_test_1", cargo.transaccionExternaId());
		assertEquals(Pago.PROCESANDO, cargo.estadoInicial());
		assertEquals("/v1/payment_intents", ultimaRuta.get());
		assertEquals("Bearer sk_test_abc", ultimoAuth.get());
		assertEquals("idem-1", ultimaIdempotencia.get());
		assertTrue(ultimoCuerpo.get().contains("amount=11440"), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("currency=pen"), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("allow_redirects]=never"), ultimoCuerpo.get());
	}

	@Test
	void aceptaLlaveRestringidaDePrueba() {
		TiendaPasarela creds = creds("rk_test_abc", TiendaPasarela.TEST);
		adapter.autorizar(creds, BigDecimal.ONE, "PEN", "idem-rk");

		assertEquals("Bearer rk_test_abc", ultimoAuth.get());
	}

	@Test
	void reembolsarCreaRefund() {
		TiendaPasarela creds = creds("sk_test_abc", TiendaPasarela.TEST);
		PaymentAdapter.ReembolsoResultado rf =
				adapter.reembolsar(creds, "pi_test_1", new BigDecimal("10.00"), "refund-idem-1");

		assertEquals("re_test_1", rf.transaccionExternaId());
		assertEquals(Reembolso.PROCESANDO, rf.estadoInicial());
		assertEquals("/v1/refunds", ultimaRuta.get());
		assertEquals("refund-idem-1", ultimaIdempotencia.get());
		assertTrue(ultimoCuerpo.get().contains("payment_intent=pi_test_1"), ultimoCuerpo.get());
		assertTrue(ultimoCuerpo.get().contains("amount=1000"), ultimoCuerpo.get());
	}

	@Test
	void enVivoSeNiega() {
		TiendaPasarela creds = creds("sk_live_x", TiendaPasarela.VIVO);
		assertThrows(IllegalStateException.class,
				() -> adapter.autorizar(creds, BigDecimal.ONE, "PEN", "i"));
		assertThrows(IllegalStateException.class,
				() -> adapter.reembolsar(creds, "pi_x", BigDecimal.ONE, "idem"));
	}

	@Test
	void modoTestTambienRechazaUnaLlaveLive() {
		TiendaPasarela creds = creds("sk_live_x", TiendaPasarela.TEST);

		assertThrows(IllegalStateException.class,
				() -> adapter.autorizar(creds, BigDecimal.ONE, "PEN", "i"));
		assertThrows(IllegalStateException.class,
				() -> adapter.reembolsar(creds, "pi_x", BigDecimal.ONE, "idem"));
		assertFalse(adapter.verificaFirma(creds, "{}", "t=1,v1=00"));
		assertNull(ultimaRuta.get(), "una llave live no debe llegar al stub/proveedor");
	}

	@Test
	void firmaEstiloStripe() throws Exception {
		String whsec = "whsec_test_123";
		TiendaPasarela creds = creds("sk_test_abc;" + whsec, TiendaPasarela.TEST);
		String cuerpo = "{\"id\":\"evt_1\",\"type\":\"payment_intent.succeeded\"}";
		String t = "1492774577";
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(whsec.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String v1 = HexFormat.of().formatHex(
				mac.doFinal((t + "." + cuerpo).getBytes(StandardCharsets.UTF_8)));

		assertTrue(adapter.verificaFirma(creds, cuerpo, "t=" + t + ",v1=" + v1));
		assertFalse(adapter.verificaFirma(creds, cuerpo + "x", "t=" + t + ",v1=" + v1));
		assertFalse(adapter.verificaFirma(creds, cuerpo, "t=" + t + ",v1=00"));
	}

	@Test
	void sinWhsecRechaza() {
		TiendaPasarela creds = creds("sk_test_abc", TiendaPasarela.TEST);
		assertFalse(adapter.verificaFirma(creds, "{}", "t=1,v1=abc"));
	}

	private TiendaPasarela creds(String secretoPlano, String modo) {
		TiendaPasarela creds = new TiendaPasarela();
		creds.setClavePublica("pk_test_abc");
		creds.setSecretoCifrado(crypto.cifrar(secretoPlano));
		creds.setModo(modo);
		creds.setActivo(true);
		return creds;
	}
}
