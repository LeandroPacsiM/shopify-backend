package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
import com.tienda.plataforma.auth.dto.LoginRequest;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.AuthService.SesionEmitida;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;

/**
 * Fase 2 (ADR-017): trial pre-2FA estilo-Shopify. Sin enrolar: login con
 * challenge, sesión limitada (`mfa=pendiente`) que sí crea tienda y queda
 * STORE_OWNER; el refresh sigue limitado; tras enrolar + verificar el JWT
 * es completo. El deny-list de la limitada llega en Fase 3.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrialPre2faTest {

	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	JwtService jwtService;
	@Autowired
	CryptoService cryptoService;
	@Autowired
	MockMvc mvc;

	private final GoogleAuthenticator google = new GoogleAuthenticator();

	@Test
	void trialCreaTiendaLimitadoYSubeACompletoTrasEnrolar() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		String email = "trial-" + tag + "@example.com";
		Usuario usuario = new Usuario();
		usuario.setEmail(email);
		usuario.setPasswordHash(passwordEncoder.encode("Secreta123"));
		usuario.setNombre("Trial");
		usuario.setApellido("Pre");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		// 1. Login pre-2FA: challenge, sin 409.
		assertTrue(authService.login(new LoginRequest(email, "Secreta123")).requiereTotp());

		// 2. Paso 2 pre-2FA: sesión limitada (sin código que verificar).
		SesionEmitida limitada = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", null, null), null, null);
		assertNotNull(limitada.accessToken());
		assertTrue(jwtService.esMfaPendiente(jwtService.verificarStaff(limitada.accessToken())));

		// 3. La limitada SÍ crea tienda: el creador nace STORE_OWNER.
		String slug = "trial-" + tag;
		mvc.perform(post("/auth/tiendas")
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + limitada.accessToken())
				.content("{\"nombre\":\"Trial " + tag + "\",\"slug\":\"" + slug + "\"}"))
				.andExpect(status().isCreated());
		Long tiendaId = tiendas.findBySlug(slug).orElseThrow().getId();
		Long usuarioId = usuarios.findByEmail(email).orElseThrow().getId();
		assertEquals(TiendaUsuario.STORE_OWNER, tiendaUsuarios
				.findByTiendaIdAndUsuarioId(tiendaId, usuarioId).orElseThrow().getRolTienda());

		// 4. Refresh pre-2FA: sigue limitado.
		SesionEmitida renovada = authService.refresh(limitada.refreshPlano(), null, null);
		assertTrue(jwtService.esMfaPendiente(jwtService.verificarStaff(renovada.accessToken())));

		// 5. Enrola + verifica: el JWT sube a completo.
		authService.iniciarEnrolamiento(new CredencialesRequest(email, "Secreta123"));
		String secreto = cryptoService.descifrar(
				usuarios.findByEmail(email).orElseThrow().getTotpSecret());
		int alta = google.getTotpPassword(secreto);
		assertTrue(authService.confirmarEnrolamiento(new ConfirmarTotpRequest(email, "Secreta123",
				String.format("%06d", alta))).habilitado());
		String secreto2 = cryptoService.descifrar(
				usuarios.findByEmail(email).orElseThrow().getTotpSecret());
		int codigo = google.getTotpPassword(secreto2);
		SesionEmitida completa = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", String.format("%06d", codigo), null),
				null, null);
		assertFalse(jwtService.esMfaPendiente(jwtService.verificarStaff(completa.accessToken())));
	}
}
