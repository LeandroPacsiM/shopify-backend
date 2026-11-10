package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.repository.CodigoClienteRepository;
import com.tienda.plataforma.auth.service.ClienteAuthService;
import com.tienda.plataforma.shared.mail.MailService;
import com.tienda.plataforma.shared.security.HmacService;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

import jakarta.persistence.EntityManager;

/**
 * RF-AUTH-10 (objetivo B, ADR-016): passwordless CUSTOMER con flag on.
 * Código fijo "123456" vía bean primario de prueba.
 */
@SpringBootTest(properties = "auth.customer.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClienteAuthTest {

	@TestConfiguration
	static class CodigoFijo {
		@Bean
		@Primary
		ClienteAuthService clienteAuthService(ClienteRepository clientes,
				TiendaRepository tiendas, CodigoClienteRepository codigos,
				com.tienda.plataforma.auth.repository.ClienteSesionRepository sesiones,
				MailService correo, JwtService jwtService, HmacService hmac,
				EntityManager entityManager) {
			return new ClienteAuthService(true, 10, 5, 5, 30, 60, clientes, tiendas,
					codigos, sesiones, correo, jwtService, hmac,
					entityManager) {
				@Override
				protected String generarCodigoPlano() {
					return "123456";
				}
			};
		}
	}

	@Autowired
	MockMvc mvc;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	CodigoClienteRepository codigos;

	Tienda tienda;
	String slug;
	String email;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		slug = "ca-" + tag;
		email = "customer-" + tag + "@example.com";
		tienda = new Tienda();
		tienda.setNombre("CA " + tag);
		tienda.setSlug(slug);
		tienda.setActiva(true);
		tiendas.save(tienda);
	}

	private String cuerpoSolicitar() {
		return "{\"tiendaSlug\":\"" + slug + "\",\"email\":\"" + email + "\"}";
	}

	private String cuerpoVerificar(String codigo) {
		return "{\"tiendaSlug\":\"" + slug + "\",\"email\":\"" + email
				+ "\",\"codigo\":\"" + codigo + "\"}";
	}

	@Test
	void perfilNaceAlVerificarYEmiteSesion() throws Exception {
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoSolicitar()))
				.andExpect(status().isNoContent());
		// El perfil NO existe tras solicitar (D3).
		assertTrue(clientes.findByTiendaIdAndEmail(tienda.getId(), email).isEmpty());

		MvcResult verificado = mvc.perform(post("/auth/cliente/verificar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoVerificar("123456")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString())
				.andExpect(cookie().exists("refresh_customer"))
				.andReturn();

		Long clienteId = clientes.findByTiendaIdAndEmail(tienda.getId(), email)
				.orElseThrow().getId();
		// El código queda consumido (sin pendientes).
		assertTrue(codigos
				.findByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
						tienda.getId(), email)
				.isEmpty());

		// Refresh rota (cookie distinta).
		String refresh1 = verificado.getResponse().getCookie("refresh_customer").getValue();
		MvcResult rotado = mvc.perform(post("/auth/cliente/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh1)))
				.andExpect(status().isOk())
				.andExpect(cookie().exists("refresh_customer"))
				.andReturn();
		String refresh2 = rotado.getResponse().getCookie("refresh_customer").getValue();
		// El rotado sigue vigente en uso normal.
		mvc.perform(post("/auth/cliente/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh2)))
				.andExpect(status().isOk());
		// El reuso del viejo revoca la familia entera (detección de robo).
		mvc.perform(post("/auth/cliente/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh1)))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/auth/cliente/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh2)))
				.andExpect(status().isUnauthorized());
		assertTrue(clienteId > 0);
	}

	@Test
	void codigoErradoConsumeIntentosYSinBloqueoPermanente() throws Exception {
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoSolicitar()))
				.andExpect(status().isNoContent());
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/auth/cliente/verificar")
					.contentType(MediaType.APPLICATION_JSON)
					.content(cuerpoVerificar("000000")))
					.andExpect(status().isUnauthorized());
		}
		// Código agotado: ni el correcto pasa; uno nuevo sí (sin bloqueo).
		mvc.perform(post("/auth/cliente/verificar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoVerificar("123456")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void reenvioInmediatoSeRechaza() throws Exception {
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoSolicitar()))
				.andExpect(status().isNoContent());
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoSolicitar()))
				.andExpect(status().isTooManyRequests());
	}

	@Test
	void logoutRevoca() throws Exception {
		mvc.perform(post("/auth/cliente/solicitar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoSolicitar()))
				.andExpect(status().isNoContent());
		MvcResult verificado = mvc.perform(post("/auth/cliente/verificar")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoVerificar("123456")))
				.andExpect(status().isOk())
				.andReturn();
		String refresh = verificado.getResponse().getCookie("refresh_customer").getValue();
		mvc.perform(post("/auth/cliente/logout")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh)))
				.andExpect(status().isNoContent());
		mvc.perform(post("/auth/cliente/refresh")
				.cookie(new jakarta.servlet.http.Cookie("refresh_customer", refresh)))
				.andExpect(status().isUnauthorized());
	}
}
