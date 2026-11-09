package com.tienda.plataforma.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** OPS-01: Ops no revela su existencia; suspender/reactivar audita con actor. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpsSeguridadTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	RegistroAuditoriaRepository auditoria;

	Tienda tienda;
	String jwtSuperAdmin;
	String jwtCliente;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("O-" + tag);
		tienda.setSlug("o-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario root = nuevoUsuario("or-" + tag);
		jwtSuperAdmin = jwtService.acceso(root.getId(), List.of("super_admin"));
		Usuario cliente = nuevoUsuario("oc-" + tag);
		jwtCliente = jwtService.acceso(cliente.getId(), List.of("cliente"));
	}

	@Test
	void sinTokenYConClienteEs404() throws Exception {
		mvc.perform(get("/api/ops/tiendas")).andExpect(status().isNotFound());
		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", "Bearer " + jwtCliente))
				.andExpect(status().isNotFound());
	}

	@Test
	void superAdminOperaYSuAccionQuedaAuditada() throws Exception {
		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", "Bearer " + jwtSuperAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.slug=='" + tienda.getSlug() + "')]").exists());

		mvc.perform(patch("/api/ops/tiendas/{id}/suspender", tienda.getId())
				.header("Authorization", "Bearer " + jwtSuperAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activa").value(false));

		var filas = auditoria.findAll().stream()
				.filter(a -> "tiendas".equals(a.getEntidad())
						&& tienda.getId().equals(a.getEntidadId())
						&& "ops.suspender".equals(a.getAccion()))
				.toList();
		assertEquals(1, filas.size());
		assertEquals(jwtSuperAdminUsuario(), filas.get(0).getUsuario().getId());

		mvc.perform(patch("/api/ops/tiendas/{id}/reactivar", tienda.getId())
				.header("Authorization", "Bearer " + jwtSuperAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activa").value(true));
	}

	private Long jwtSuperAdminUsuario() {
		return Long.valueOf(jwtService.verificar(jwtSuperAdmin).getSubject());
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
}
