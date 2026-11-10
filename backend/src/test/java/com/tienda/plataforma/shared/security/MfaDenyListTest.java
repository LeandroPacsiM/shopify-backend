package com.tienda.plataforma.shared.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.ConfirmarTotpRequest;
import com.tienda.plataforma.auth.dto.CredencialesRequest;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.AuthService.SesionEmitida;
import com.warrenstrange.googleauth.GoogleAuthenticator;

/**
 * Fase 3 (ADR-017): deny-list de la sesión limitada (`mfa=pendiente`).
 * Lee (GETs) pero toda escritura bajo `/api/admin/**` es 403; Ops ni
 * siquiera la mira (sin roles). Tras enrolar, el JWT completo escribe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MfaDenyListTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JwtService jwtService;
	@Autowired
	CryptoService cryptoService;

	private final GoogleAuthenticator google = new GoogleAuthenticator();

	String email;
	String slug;
	String jwtLimitado;

	@BeforeEach
	void trialConTienda() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		email = "deny-" + tag + "@example.com";
		slug = "deny-" + tag;
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode("Secreta123"));
		usuario.setNombre("Deny");
		usuario.setApellido("List");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		SesionEmitida limitada = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", null, null), null, null);
		jwtLimitado = limitada.accessToken();

		mvc.perform(post("/auth/tiendas")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + jwtLimitado)
				.content("{\"nombre\":\"Deny " + tag + "\",\"slug\":\"" + slug + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void limitadaLeePeroNoEscribeEnAdmin() throws Exception {
		// Lectura: permitida (es STORE_OWNER de su tienda).
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug))
				.andExpect(status().isOk());
		mvc.perform(get("/api/admin/facturacion")
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug))
				.andExpect(status().isOk());

		// Escritura: 403 en catálogo, equipo, cobros y propiedad.
		mvc.perform(post("/api/admin/productos")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug)
				.content("{\"titulo\":\"Café\",\"slug\":\"cafe\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/usuarios")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug)
				.content("{\"email\":\"otro@example.com\",\"rol\":\"operador\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/facturacion/suscribir")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug)
				.content("{\"plan\":\"mensual\"}"))
				.andExpect(status().isForbidden());
		mvc.perform(patch("/api/admin/tienda/propietario")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + jwtLimitado)
				.header("X-Tienda-Slug", slug)
				.content("{\"nuevoOwnerId\":999}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void limitadaNoEntraAOps() throws Exception {
		// Ops niega en sigilo con 404 (sin rastro de la superficie).
		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", "Bearer " + jwtLimitado))
				.andExpect(status().isNotFound());
	}

	@Test
	void trasEnrolarElJwtCompletoEscribe() throws Exception {
		authService.iniciarEnrolamiento(new CredencialesRequest(email, "Secreta123"));
		String secreto = cryptoService.descifrar(
				usuarios.findByEmail(email).orElseThrow().getTotpSecret());
		int alta = google.getTotpPassword(secreto);
		authService.confirmarEnrolamiento(new ConfirmarTotpRequest(email, "Secreta123",
				String.format("%06d", alta)));
		String secreto2 = cryptoService.descifrar(
				usuarios.findByEmail(email).orElseThrow().getTotpSecret());
		int codigo = google.getTotpPassword(secreto2);
		SesionEmitida completa = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", String.format("%06d", codigo), null),
				null, null);
		assertFalse(jwtService.esMfaPendiente(jwtService.verificarStaff(completa.accessToken())));

		mvc.perform(post("/api/admin/productos")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + completa.accessToken())
				.header("X-Tienda-Slug", slug)
				.content("{\"titulo\":\"Café\",\"slug\":\"cafe\"}"))
				.andExpect(status().isOk());
	}
}
