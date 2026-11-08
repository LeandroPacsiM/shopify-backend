package com.tienda.plataforma.platform.facturacion;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionEventoRepository;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.venta.service.PedidoService;

/** RF-BILL: suscripción por tienda, freeze por impago, webhook idempotente. Rollback al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "stripe.platform-whsec=whsec_test_fact")
@Transactional
class FacturacionTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	FacturacionService facturacion;
	@Autowired
	PedidoService pedidos;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	SuscripcionRepository suscripciones;
	@Autowired
	SuscripcionEventoRepository eventos;

	Tienda tienda;
	Usuario admin;
	Usuario cliente;
	String jwtAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("F-" + tag);
		tienda.setSlug("f-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		admin = nuevoUsuario("fa-" + tag);
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(admin);
		vinculo.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(vinculo);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		cliente = nuevoUsuario("fc-" + tag);
		Cliente c = new Cliente();
		c.setUsuario(cliente);
		c.setTienda(tienda);
		c.setAceptaMarketing(false);
		clientes.save(c);
	}

	@Test
	void suscribirVerCancelar() throws Exception {
		mvc.perform(post("/api/admin/facturacion/suscribir")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"plan\":\"mensual\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("pendiente"));

		mvc.perform(get("/api/admin/facturacion")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("mensual"));

		mvc.perform(post("/api/admin/facturacion/cancelar")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("cancelada"));

		assertThrows(ConflictoException.class,
				() -> facturacion.suscribir(tienda.getId(), "trimestral"));
	}

	@Test
	void sinSuscripcionNoHayCheckout() {
		assertThrows(ConflictoException.class,
				() -> pedidos.crearCheckout(cliente.getId(), tienda.getSlug(), null));
	}

	@Test
	void webhookPagadaActivaYDuplicadoUnaVez() throws Exception {
		conSuscripcion("sub_test_1");
		String cuerpo = "{\"id\":\"evt-f1\"}";
		String firma = firma(cuerpo);

		String envio = "{\"eventId\":\"evt-f1\",\"suscripcionId\":\"sub_test_1\","
				+ "\"tipo\":\"suscripcion.pagada\",\"firma\":\"" + firma + "\","
				+ "\"cuerpo\":" + json(cuerpo) + "}";
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/webhooks/facturacion")
					.contentType(MediaType.APPLICATION_JSON)
					.content(envio))
					.andExpect(status().isOk());
		}
		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertEquals(Suscripcion.ACTIVA, s.getEstado());
		assertTrue(s.getPeriodoFin().isAfter(Instant.now().plusSeconds(29 * 24 * 60 * 60)));
		assertTrue(tiendas.findById(tienda.getId()).orElseThrow().getActiva());
		assertTrue(eventos.existsByReferenciaExterna("evt-f1"));

		facturacion.exigirActiva(tienda.getId());
	}

	@Test
	void webhookVencidaCongela() throws Exception {
		conSuscripcion("sub_test_2");
		activar("sub_test_2", "evt-f2");
		assertTrue(tiendas.findById(tienda.getId()).orElseThrow().getActiva());

		String cuerpo = "{\"id\":\"evt-f3\"}";
		mvc.perform(post("/api/webhooks/facturacion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"evt-f3\",\"suscripcionId\":\"sub_test_2\","
						+ "\"tipo\":\"suscripcion.vencida\",\"firma\":\"" + firma(cuerpo) + "\","
						+ "\"cuerpo\":" + json(cuerpo) + "}"))
				.andExpect(status().isOk());

		assertEquals(Suscripcion.VENCIDA,
				suscripciones.findByTiendaId(tienda.getId()).orElseThrow().getEstado());
		assertFalse(tiendas.findById(tienda.getId()).orElseThrow().getActiva());
		assertThrows(ConflictoException.class,
				() -> facturacion.exigirActiva(tienda.getId()));
	}

	@Test
	void firmaInvalidaSeRechaza() throws Exception {
		conSuscripcion("sub_test_3");
		mvc.perform(post("/api/webhooks/facturacion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"evt-fx\",\"suscripcionId\":\"sub_test_3\","
						+ "\"tipo\":\"suscripcion.pagada\",\"firma\":\"t=1,v1=00\","
						+ "\"cuerpo\":\"{\\\"id\\\":\\\"evt-fx\\\"}\"}"))
				.andExpect(status().isUnauthorized());
	}

	private void conSuscripcion(String stripeId) {
		Suscripcion s = new Suscripcion();
		s.setTienda(tienda);
		s.setPlan(Suscripcion.MENSUAL);
		s.setEstado(Suscripcion.PENDIENTE);
		s.setStripeSubscriptionId(stripeId);
		suscripciones.save(s);
	}

	private void activar(String stripeId, String eventId) throws Exception {
		String cuerpo = "{\"id\":\"" + eventId + "\"}";
		mvc.perform(post("/api/webhooks/facturacion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"eventId\":\"" + eventId + "\",\"suscripcionId\":\"" + stripeId + "\","
						+ "\"tipo\":\"suscripcion.pagada\",\"firma\":\"" + firma(cuerpo) + "\","
						+ "\"cuerpo\":" + json(cuerpo) + "}"))
				.andExpect(status().isOk());
	}

	private Usuario nuevoUsuario(String tag) {
		Usuario u = new Usuario();
		u.setEmail(tag + "@example.com");
		u.setPasswordHash("x");
		u.setActivo(true);
		u.setEmailVerificado(true);
		u.setIntentosFallidos(0);
		return usuarios.save(u);
	}

	private String firma(String cuerpo) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec("whsec_test_fact".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String v1 = HexFormat.of().formatHex(
				mac.doFinal(("1." + cuerpo).getBytes(StandardCharsets.UTF_8)));
		return "t=1,v1=" + v1;
	}

	private String json(String cuerpo) {
		return "\"" + cuerpo.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}
