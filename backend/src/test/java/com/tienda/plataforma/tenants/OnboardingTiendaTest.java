package com.tienda.plataforma.tenants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** RF-TENANT-04: el comerciante crea su tienda y queda admin en trial. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OnboardingTiendaTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository vinculos;
	@Autowired
	SuscripcionRepository suscripciones;
	@Autowired
	RegistroAuditoriaRepository auditoria;
	@Autowired
	FacturacionService facturacion;

	Usuario comerciante;
	String jwt;

	@BeforeEach
	void fixtures() {
		comerciante = new Usuario();
		comerciante.setEmail("ob-" + UUID.randomUUID() + "@example.com");
		comerciante.setPasswordHash("x");
		comerciante.setActivo(true);
		comerciante.setEmailVerificado(true);
		comerciante.setIntentosFallidos(0);
		usuarios.save(comerciante);
		jwt = jwtService.acceso(comerciante.getId(), List.of("cliente"));
	}

	@Test
	void creaTiendaQuedaAdminYSinCheckout() throws Exception {
		String slug = "tienda-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/auth/tiendas")
				.header("Authorization", "Bearer " + jwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Mi Tienda\",\"slug\":\"" + slug + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.slug").value(slug))
				.andExpect(jsonPath("$.activa").value(true));

		Tienda tienda = tiendas.findBySlug(slug).orElseThrow();
		assertTrue(tienda.getActiva());
		assertEquals(TiendaUsuario.ADMIN, vinculos
				.findByTiendaIdAndUsuarioId(tienda.getId(), comerciante.getId())
				.orElseThrow().getRolTienda());
		Suscripcion s = suscripciones.findByTiendaId(tienda.getId()).orElseThrow();
		assertEquals(Suscripcion.PENDIENTE, s.getEstado());
		assertEquals(Suscripcion.MENSUAL, s.getPlan());
		assertTrue(auditoria.findByEntidadAndEntidadId("tiendas", tienda.getId()).stream()
				.anyMatch(a -> "tienda.crear".equals(a.getAccion())
						&& comerciante.getId().equals(a.getUsuario().getId())));
		assertThrows(ConflictoException.class, () -> facturacion.exigirActiva(tienda.getId()));

		mvc.perform(post("/auth/tiendas")
				.header("Authorization", "Bearer " + jwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Otra\",\"slug\":\"" + slug + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void sinTokenYSslugInvalidoSeRechazan() throws Exception {
		mvc.perform(post("/auth/tiendas")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"X\",\"slug\":\"x relevance\"}"))
				.andExpect(status().isForbidden());

		mvc.perform(post("/auth/tiendas")
				.header("Authorization", "Bearer " + jwt)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"X\",\"slug\":\"SLUG MALO\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unUsuarioPuedeTenerVariasTiendas() throws Exception {
		for (String slug : List.of("multi-a-" + UUID.randomUUID().toString().substring(0, 4),
				"multi-b-" + UUID.randomUUID().toString().substring(0, 4))) {
			mvc.perform(post("/auth/tiendas")
					.header("Authorization", "Bearer " + jwt)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"nombre\":\"" + slug + "\",\"slug\":\"" + slug + "\"}"))
					.andExpect(status().isCreated());
		}
		assertEquals(2, vinculos.findByUsuarioId(comerciante.getId()).size());
	}
}
