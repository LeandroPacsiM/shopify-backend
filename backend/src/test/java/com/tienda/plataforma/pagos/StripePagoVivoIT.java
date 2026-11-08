package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.pagos.service.PagoService;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/**
 * E2E EN VIVO con Stripe (temporal). Sin @Transactional a propósito: el
 * backend corriendo en :8080 debe ver las filas para procesar el webhook
 * que entra por `stripe listen`. Requiere backend + listen activos.
 * Lee llaves SOLO del entorno; sin ellas se omite.
 */
@SpringBootTest
@ActiveProfiles("local")
class StripePagoVivoIT {

	@Autowired
	PagoService pagoService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PasarelaPagoRepository pasarelas;
	@Autowired
	TiendaPasarelaRepository tiendaPasarelas;
	@Autowired
	PagoRepository pagos;
	@Autowired
	CryptoService crypto;

	@Test
	void pagoCompletoConWebhookReal() throws Exception {
		String sk = System.getenv("STRIPE_API_KEY");
		String whsec = System.getenv("STRIPE_TIENDA_WHSEC");
		assumeTrue(sk != null && (sk.startsWith("sk_test_") || sk.startsWith("rk_test_")), "sin STRIPE_API_KEY; se omite");
		assumeTrue(whsec != null && whsec.startsWith("whsec_"), "sin STRIPE_TIENDA_WHSEC; se omite");

		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("SV-" + tag);
		tienda.setSlug("sv-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario usuario = new Usuario();
		usuario.setEmail("sv-" + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		Cliente cliente = new Cliente();
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		clientes.save(cliente);

		Pedido pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido("SV-" + tag);
		pedido.setEstado(Pedido.PENDIENTE);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(new BigDecimal("20.00"));
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(new BigDecimal("20.00"));
		pedidos.save(pedido);

		PasarelaPago pasarela = pasarelas.findByCodigo("stripe").orElseGet(() -> {
			PasarelaPago nueva = new PasarelaPago();
			nueva.setNombre("Stripe");
			nueva.setCodigo("stripe");
			nueva.setActivo(true);
			return pasarelas.save(nueva);
		});

		TiendaPasarela creds = new TiendaPasarela();
		creds.setTienda(tienda);
		creds.setPasarela(pasarela);
		creds.setClavePublica("pk_test_e2e");
		creds.setSecretoCifrado(crypto.cifrar(sk + ";" + whsec));
		creds.setModo(TiendaPasarela.TEST);
		creds.setActivo(true);
		tiendaPasarelas.save(creds);

		var intento = pagoService.crearIntento(usuario.getId(), pedido.getId(), "stripe", "sv-" + tag);
		assertTrue(intento.transaccionExternaId().startsWith("pi_"), intento.transaccionExternaId());

		confirmarConVisa(sk, intento.transaccionExternaId());

		Long pagoId = pagos.findByPasarelaIdAndTransaccionExternaId(
				pasarela.getId(), intento.transaccionExternaId()).orElseThrow().getId();
		boolean pagado = false;
		for (int i = 0; i < 30 && !pagado; i++) {
			Thread.sleep(1000);
			pagado = Pago.APROBADO.equals(pagos.findById(pagoId).orElseThrow().getEstado());
		}
		assertTrue(pagado, "el webhook no marcó aprobado en 30s (¿corre backend + listen?)");
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
	}

	private void confirmarConVisa(String sk, String pi) throws Exception {
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		String cuerpo = "payment_method=" + URLEncoder.encode("pm_card_visa", StandardCharsets.UTF_8);
		HttpResponse<String> r = http.send(
				HttpRequest.newBuilder()
						.uri(URI.create("https://api.stripe.com/v1/payment_intents/" + pi + "/confirm"))
						.timeout(Duration.ofSeconds(20))
						.header("Authorization", "Bearer " + sk)
						.header("Content-Type", "application/x-www-form-urlencoded")
						.POST(HttpRequest.BodyPublishers.ofString(cuerpo))
						.build(),
				HttpResponse.BodyHandlers.ofString());
		assertTrue(r.statusCode() >= 200 && r.statusCode() < 300, r.body());
	}
}
