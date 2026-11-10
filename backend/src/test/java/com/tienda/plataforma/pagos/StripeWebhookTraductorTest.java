package com.tienda.plataforma.pagos;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.entity.Reembolso;
import com.tienda.plataforma.pagos.entity.TiendaPasarela;
import com.tienda.plataforma.pagos.repository.PagoEventoRepository;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.pagos.repository.ReembolsoRepository;
import com.tienda.plataforma.pagos.repository.TiendaPasarelaRepository;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** Traductor Stripe→envelope: firma, mapeo, dedup y montos. Sin red (firmas HMAC locales). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StripeWebhookTraductorTest {

	static final String WHSEC = "whsec_test_trad";

	@Autowired
	MockMvc mvc;
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
	PagoEventoRepository eventos;
	@Autowired
	ReembolsoRepository reembolsos;
	@Autowired
	CryptoService crypto;

	Pedido pedido;
	Pago pago;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("ST-" + tag);
		tienda.setSlug("st-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario usuario = new Usuario();
		usuario.setEmail("st-" + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		Cliente cliente = new Cliente();
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(usuario.getEmail());
		clientes.save(cliente);

		pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido("ST-" + tag);
		pedido.setEstado(Pedido.PENDIENTE);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(new BigDecimal("100.00"));
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(new BigDecimal("100.00"));
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
		creds.setClavePublica("pk_test_x");
		creds.setSecretoCifrado(crypto.cifrar("sk_test_x;" + WHSEC));
		creds.setModo(TiendaPasarela.TEST);
		creds.setActivo(true);
		tiendaPasarelas.save(creds);

		pago = new Pago();
		pago.setPedido(pedido);
		pago.setPasarela(pasarela);
		pago.setEstado(Pago.PROCESANDO);
		pago.setMonto(new BigDecimal("100.00"));
		pago.setMoneda("PEN");
		pago.setTransaccionExternaId("pi_test_" + tag);
		pagos.save(pago);
	}

	@Test
	void succeededApruebaYDuplicadoUnaVez() throws Exception {
		String cuerpo = pi("evt-st-1", "payment_intent.succeeded", 10000);
		for (int i = 0; i < 2; i++) {
			enviar(cuerpo).andExpect(status().isOk());
		}
		assertEquals(Pago.APROBADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
		assertEquals(1, eventos.findByPagoId(pago.getId()).stream()
				.filter(e -> "evt-st-1".equals(e.getReferenciaExterna())).count());
	}

	@Test
	void failedRechaza() throws Exception {
		enviar(pi("evt-st-2", "payment_intent.payment_failed", 10000))
				.andExpect(status().isOk());
		assertEquals(Pago.RECHAZADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
	}

	@Test
	void refundConfirmaReembolso() throws Exception {
		pago.setEstado(Pago.APROBADO);
		pagos.save(pago);
		pedido.setEstado(Pedido.PAGADO);
		pedidos.save(pedido);
		Reembolso r = new Reembolso();
		r.setPedido(pedido);
		r.setPago(pago);
		r.setMonto(new BigDecimal("10.00"));
		r.setMoneda("PEN");
		r.setEstado(Reembolso.PROCESANDO);
		r.setTransaccionExternaId("re_test_1");
		reembolsos.save(r);

		String cuerpo = "{\"id\":\"evt-st-3\",\"type\":\"refund.succeeded\","
				+ "\"data\":{\"object\":{\"id\":\"re_test_1\",\"amount\":1000,"
				+ "\"payment_intent\":\"" + pago.getTransaccionExternaId() + "\"}}}";
		enviar(cuerpo).andExpect(status().isOk());
		assertEquals(Reembolso.APROBADO,
				reembolsos.findById(r.getId()).orElseThrow().getEstado());
		assertEquals(Pago.APROBADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
	}

	@Test
	void soloReembolsoTotalConfirmadoCambiaPagoYPedido() throws Exception {
		pago.setEstado(Pago.APROBADO);
		pagos.save(pago);
		pedido.setEstado(Pedido.PAGADO);
		pedidos.save(pedido);
		Reembolso r = new Reembolso();
		r.setPedido(pedido);
		r.setPago(pago);
		r.setMonto(new BigDecimal("100.00"));
		r.setMoneda("PEN");
		r.setEstado(Reembolso.PROCESANDO);
		r.setTransaccionExternaId("re_test_full");
		reembolsos.save(r);

		String cuerpo = "{\"id\":\"evt-st-full\",\"type\":\"refund.succeeded\","
				+ "\"data\":{\"object\":{\"id\":\"re_test_full\",\"amount\":10000,"
				+ "\"payment_intent\":\"" + pago.getTransaccionExternaId() + "\"}}}";
		enviar(cuerpo).andExpect(status().isOk());

		assertEquals(Reembolso.APROBADO, reembolsos.findById(r.getId()).orElseThrow().getEstado());
		assertEquals(Pago.REEMBOLSADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.REEMBOLSADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
	}

	@Test
	void reembolsoFallidoNoCambiaElPagoNiElPedido() throws Exception {
		pago.setEstado(Pago.APROBADO);
		pagos.save(pago);
		pedido.setEstado(Pedido.PAGADO);
		pedidos.save(pedido);
		Reembolso r = new Reembolso();
		r.setPedido(pedido);
		r.setPago(pago);
		r.setMonto(new BigDecimal("100.00"));
		r.setMoneda("PEN");
		r.setEstado(Reembolso.PROCESANDO);
		r.setTransaccionExternaId("re_test_failed");
		reembolsos.save(r);

		String cuerpo = "{\"id\":\"evt-st-fail\",\"type\":\"refund.failed\","
				+ "\"data\":{\"object\":{\"id\":\"re_test_failed\",\"amount\":10000,"
				+ "\"payment_intent\":\"" + pago.getTransaccionExternaId() + "\"}}}";
		enviar(cuerpo).andExpect(status().isOk());

		assertEquals(Reembolso.RECHAZADO, reembolsos.findById(r.getId()).orElseThrow().getEstado());
		assertEquals(Pago.APROBADO, pagos.findById(pago.getId()).orElseThrow().getEstado());
		assertEquals(Pedido.PAGADO, pedidos.findById(pedido.getId()).orElseThrow().getEstado());
	}

	@Test
	void firmaMalaMontoMaloTipoRaro() throws Exception {
		String cuerpo = pi("evt-st-4", "payment_intent.succeeded", 10000);
		mvc.perform(post("/api/webhooks/pagos/stripe")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", "t=1,v1=00")
				.content(cuerpo))
				.andExpect(status().isUnauthorized());

		enviar(pi("evt-st-5", "payment_intent.succeeded", 9999))
				.andExpect(status().isConflict());

		String raro = "{\"id\":\"evt-st-6\",\"type\":\"coupon.created\","
				+ "\"data\":{\"object\":{\"id\":\"co_1\"}}}";
		enviar(raro).andExpect(status().isOk());
		assertEquals(Pago.PROCESANDO, pagos.findById(pago.getId()).orElseThrow().getEstado());
	}

	private String pi(String evt, String tipo, long centimos) {
		return "{\"id\":\"" + evt + "\",\"type\":\"" + tipo + "\","
				+ "\"data\":{\"object\":{\"id\":\"" + pago.getTransaccionExternaId()
				+ "\",\"amount\":" + centimos + "}}}";
	}

	private org.springframework.test.web.servlet.ResultActions enviar(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(WHSEC.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String v1 = HexFormat.of().formatHex(
				mac.doFinal(("1." + cuerpo).getBytes(StandardCharsets.UTF_8)));
		return mvc.perform(post("/api/webhooks/pagos/stripe")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Stripe-Signature", "t=1,v1=" + v1)
				.content(cuerpo));
	}
}
