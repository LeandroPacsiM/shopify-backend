package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.CrearTiendaRequest;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.AuthService.SesionEmitida;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.service.TiendaOnboardingService;

/**
 * `GET /auth/tiendas`: el comerciante ve sus membresías (vacío, una,
 * varias), con JWT limitado y completo. Solo lectura, sin esquema nuevo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MisTiendasTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	AuthService authService;
	@Autowired
	TiendaOnboardingService onboarding;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void vacioUnaYVariasConJwtLimitado() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		String email = "mias-" + tag + "@example.com";
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode("Secreta123"));
		usuario.setNombre("Mías");
		usuario.setApellido("Tiendas");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);
		Long usuarioId = usuarios.findByEmail(email).orElseThrow().getId();

		SesionEmitida limitada = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", null, null), null, null);

		// Vacío recién registrado.
		mvc.perform(get("/auth/tiendas")
				.header("Authorization", "Bearer " + limitada.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$.length()").value(0));

		onboarding.crear(usuarioId, new CrearTiendaRequest("Una " + tag, "mias-" + tag, null));
		onboarding.crear(usuarioId, new CrearTiendaRequest("Dos " + tag, "mias-dos-" + tag, null));

		mvc.perform(get("/auth/tiendas")
				.header("Authorization", "Bearer " + limitada.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].slug",
						Matchers.containsInAnyOrder("mias-" + tag, "mias-dos-" + tag)))
				.andExpect(jsonPath("$[*].rolTienda",
						Matchers.everyItem(Matchers.is(TiendaUsuario.STORE_OWNER))));

		// Sin token no entra.
		mvc.perform(get("/auth/tiendas")).andExpect(status().isForbidden());
	}

	@Test
	void crearPorEndpointTambienAparece() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		String email = "endpoint-" + tag + "@example.com";
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode("Secreta123"));
		usuario.setNombre("E");
		usuario.setApellido("P");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		SesionEmitida limitada = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", null, null), null, null);

		mvc.perform(post("/auth/tiendas")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + limitada.accessToken())
				.content("{\"nombre\":\"EP " + tag + "\",\"slug\":\"ep-" + tag + "\"}"))
				.andExpect(status().isCreated());

		mvc.perform(get("/auth/tiendas")
				.header("Authorization", "Bearer " + limitada.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].slug").value("ep-" + tag));
	}
}
