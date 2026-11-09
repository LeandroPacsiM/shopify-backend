package com.tienda.plataforma.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.security.JwtService;

/** OPS-01: con allowlist activa, una IP fuera de ella ve 404 aunque sea super_admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.ops.ips-permitidas=10.9.9.9")
@Transactional
class OpsIpTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;

	String jwtSuperAdmin;

	@BeforeEach
	void fixtures() {
		Usuario root = new Usuario();
		root.setEmail("oip-" + UUID.randomUUID() + "@example.com");
		root.setPasswordHash("x");
		root.setActivo(true);
		root.setEmailVerificado(true);
		root.setIntentosFallidos(0);
		usuarios.save(root);
		jwtSuperAdmin = jwtService.acceso(root.getId(), List.of("super_admin"));
	}

	@Test
	void ipFueraDeLaListaEs404() throws Exception {
		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", "Bearer " + jwtSuperAdmin))
				.andExpect(status().isNotFound());
	}
}
