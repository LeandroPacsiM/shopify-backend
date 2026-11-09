package com.tienda.plataforma.envios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.envios.entity.Envio;
import com.tienda.plataforma.envios.repository.EnvioEventoRepository;
import com.tienda.plataforma.envios.repository.EnvioRepository;
import com.tienda.plataforma.envios.security.TrackingWebhookAuthenticator;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** Webhook de tracking: HMAC, ventana anti-replay y deduplicación transaccional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EnvioTrackingWebhookTest {

	private static final String SECRET = "tracking-webhook-integration-secret-32-bytes-minimum";

	@DynamicPropertySource
	static void configurarSecret(DynamicPropertyRegistry properties) {
		properties.add("tracking.webhook-secret", () -> SECRET);
	}

	@Autowired
	MockMvc mvc;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	EnvioRepository envios;
	@Autowired
	EnvioEventoRepository eventos;

	Envio envio;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("TW-" + tag);
		tienda.setSlug("tw-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario usuario = new Usuario();
		usuario.setEmail("tw-" + tag + "@example.com");
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
		pedido.setNumeroPedido("TW-" + tag);
		pedido.setEstado(Pedido.PAGADO);
		pedidos.save(pedido);

		envio = new Envio();
		envio.setPedido(pedido);
		envio.setNumeroTracking("TRK-TW-" + tag);
		envio.setEstado(Envio.PREPARANDO);
		envios.save(envio);
	}

	@Test
	void webhookSinFirmaEsRechazado() throws Exception {
		mvc.perform(post("/api/webhooks/envios/tracking")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("evt-no-firma")))
				.andExpect(status().isUnauthorized());

		assertEquals(0, eventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).size());
	}

	@Test
	void firmaIncorrectaOTimestampVencidoNoMutanElEnvio() throws Exception {
		byte[] cuerpo = body("evt-invalid");
		String actual = Long.toString(Instant.now().getEpochSecond());
		mvc.perform(post("/api/webhooks/envios/tracking")
				.contentType(MediaType.APPLICATION_JSON)
				.header(TrackingWebhookAuthenticator.HEADER_TIMESTAMP, actual)
				.header(TrackingWebhookAuthenticator.HEADER_SIGNATURE, "00".repeat(32))
				.content(cuerpo))
				.andExpect(status().isUnauthorized());

		String vencido = Long.toString(Instant.now().minusSeconds(10 * 60).getEpochSecond());
		mvc.perform(firmada(cuerpo, vencido))
				.andExpect(status().isUnauthorized());

		assertEquals(0, eventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).size());
		assertEquals(Envio.PREPARANDO, envios.findById(envio.getId()).orElseThrow().getEstado());
	}

	@Test
	void eventoFirmadoSeAplicaUnaVezAunqueElTransportistaReintente() throws Exception {
		byte[] cuerpo = body("evt-" + UUID.randomUUID());
		String timestamp = Long.toString(Instant.now().getEpochSecond());

		mvc.perform(firmada(cuerpo, timestamp)).andExpect(status().isOk());
		mvc.perform(firmada(cuerpo, timestamp)).andExpect(status().isOk());

		assertEquals(1, eventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).size());
		assertEquals(Envio.EN_TRANSITO, envios.findById(envio.getId()).orElseThrow().getEstado());
	}

	@Test
	void referenciaExternaEsObligatoria() throws Exception {
		byte[] cuerpo = ("{\"numeroTracking\":\"" + envio.getNumeroTracking()
				+ "\",\"estado\":\"en_transito\"}").getBytes(StandardCharsets.UTF_8);
		String timestamp = Long.toString(Instant.now().getEpochSecond());

		mvc.perform(firmada(cuerpo, timestamp)).andExpect(status().isBadRequest());
		assertEquals(0, eventos.findByEnvioIdOrderByOcurridoEnAsc(envio.getId()).size());
	}

	private org.springframework.test.web.servlet.RequestBuilder firmada(byte[] cuerpo, String timestamp)
			throws Exception {
		return post("/api/webhooks/envios/tracking")
				.contentType(MediaType.APPLICATION_JSON)
				.header(TrackingWebhookAuthenticator.HEADER_TIMESTAMP, timestamp)
				.header(TrackingWebhookAuthenticator.HEADER_SIGNATURE, firma(cuerpo, timestamp))
				.content(cuerpo);
	}

	private byte[] body(String referencia) {
		return ("{\"numeroTracking\":\"" + envio.getNumeroTracking()
				+ "\",\"estado\":\"en_transito\",\"referencia\":\"" + referencia + "\"}")
				.getBytes(StandardCharsets.UTF_8);
	}

	private String firma(byte[] cuerpo, String timestamp) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(mac.doFinal(cuerpo));
	}
}
