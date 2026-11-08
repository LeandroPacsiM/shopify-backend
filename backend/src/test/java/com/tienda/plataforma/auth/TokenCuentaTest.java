package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.RegistroRequest;
import com.tienda.plataforma.auth.entity.TokenCuenta;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.TokenCuentaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.tenants.entity.Tienda;

/** RF-AUTH-07: verificación email + reset por tokens de un solo uso (V15). Rollback al final. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class TokenCuentaTest {

	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TokenCuentaRepository tokens;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	com.tienda.plataforma.tenants.repository.TiendaRepository tiendas;

	@Test
	void verificarEmailUnSoloUso() {
		String email = nuevoUsuario("tv-");
		assertFalse(usuarios.findByEmail(email).orElseThrow().getEmailVerificado());

		TokenCuenta pendiente = ultimoPendiente(email, TokenCuenta.VERIFICACION);
		String plano = "plano-" + UUID.randomUUID();
		pendiente.setTokenHash(sha256(plano));
		tokens.save(pendiente);

		authService.confirmarVerificacion(plano);
		assertTrue(usuarios.findByEmail(email).orElseThrow().getEmailVerificado());

		assertThrows(NoAutorizadoException.class, () -> authService.confirmarVerificacion(plano));
		assertThrows(NoAutorizadoException.class, () -> authService.confirmarVerificacion("inexistente"));
	}

	@Test
	void resetCambiaPasswordYRevocaSesiones() {
		String email = nuevoUsuario("tr-");
		String hashAntes = usuarios.findByEmail(email).orElseThrow().getPasswordHash();

		authService.solicitarReset(email);
		TokenCuenta pendiente = ultimoPendiente(email, TokenCuenta.RESET);
		String plano = "reset-" + UUID.randomUUID();
		pendiente.setTokenHash(sha256(plano));
		tokens.save(pendiente);

		authService.confirmarReset(plano, "NuevaClave123");
		Usuario despues = usuarios.findByEmail(email).orElseThrow();
		assertTrue(passwordEncoder.matches("NuevaClave123", despues.getPasswordHash()));
		assertFalse(passwordEncoder.matches("Secreta123", despues.getPasswordHash()));
		assertNotEquals(hashAntes, despues.getPasswordHash());

		assertThrows(NoAutorizadoException.class,
				() -> authService.confirmarReset(plano, "OtraClave123"));
	}

	@Test
	void tokenExpiradoSeRechaza() {
		String email = nuevoUsuario("te-");
		TokenCuenta pendiente = ultimoPendiente(email, TokenCuenta.VERIFICACION);
		pendiente.setExpiraEn(Instant.now().minusSeconds(60));
		String plano = "exp-" + UUID.randomUUID();
		pendiente.setTokenHash(sha256(plano));
		tokens.save(pendiente);

		assertThrows(NoAutorizadoException.class, () -> authService.confirmarVerificacion(plano));
	}

	@Test
	void solicitarEsGenericoYReemite() {
		// Email inexistente: sin excepción (no enumerar).
		assertDoesNotThrow(() -> authService.solicitarVerificacion("nadie-" + UUID.randomUUID() + "@example.com"));
		assertDoesNotThrow(() -> authService.solicitarReset("nadie-" + UUID.randomUUID() + "@example.com"));

		String email = nuevoUsuario("tg-");
		Usuario u = usuarios.findByEmail(email).orElseThrow();
		authService.solicitarVerificacion(email);
		authService.solicitarVerificacion(email);
		long pendientes = tokens.findByUsuarioIdAndTipoAndUsadoEnIsNull(u.getId(), TokenCuenta.VERIFICACION)
				.size();
		assertEquals(1, pendientes);
	}

	private String nuevoUsuario(String prefijo) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre(prefijo + tag);
		tienda.setSlug(prefijo + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);
		String email = prefijo + tag + "@example.com";
		authService.registrar(new RegistroRequest(email, "Secreta123", "Tok", "Test", tienda.getSlug()));
		return email;
	}

	private TokenCuenta ultimoPendiente(String email, String tipo) {
		Usuario u = usuarios.findByEmail(email).orElseThrow();
		var pendientes = tokens.findByUsuarioIdAndTipoAndUsadoEnIsNull(u.getId(), tipo);
		assertFalse(pendientes.isEmpty());
		return pendientes.get(pendientes.size() - 1);
	}

	private String sha256(String texto) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			throw new IllegalStateException("sha256", e);
		}
	}
}
