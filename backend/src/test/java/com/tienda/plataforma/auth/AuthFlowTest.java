package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.ConfirmarTotpRequest;
import com.tienda.plataforma.auth.dto.CredencialesRequest;
import com.tienda.plataforma.auth.dto.LoginRequest;
import com.tienda.plataforma.auth.dto.RegistroRequest;
import com.tienda.plataforma.auth.dto.TotpLoginRequest;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.auth.service.AuthService.SesionEmitida;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.CryptoService;
import com.tienda.plataforma.shared.security.JwtService;
import com.warrenstrange.googleauth.GoogleAuthenticator;

import org.junit.jupiter.api.Test;

/** Flujo auth punta a punta a nivel servicio (diagrama 04). Rollback al final. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AuthFlowTest {

	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	com.tienda.plataforma.tenants.repository.TiendaRepository tiendas;
	@Autowired
	com.tienda.plataforma.auth.repository.TokenCuentaRepository tokens;
	@Autowired
	CryptoService cryptoService;
	@Autowired
	JwtService jwtService;

	private final GoogleAuthenticator google = new GoogleAuthenticator();

	@Test
	void flujoCompleto() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		String email = "flow-" + tag + "@example.com";

		com.tienda.plataforma.tenants.entity.Tienda tienda = new com.tienda.plataforma.tenants.entity.Tienda();
		tienda.setNombre("Flow " + tag);
		tienda.setSlug("flow-" + tag);
		tienda.setActiva(true);
		tienda = tiendas.save(tienda);

		var reg = authService.registrar(new RegistroRequest(email, "Secreta123", "Flow", "Test", tienda.getSlug()));
		assertTrue(reg.requiereEnrolamientoTotp());

		assertThrows(ConflictoException.class, () -> authService.registrar(
				new RegistroRequest(email, "Secreta123", "F", "T", "tienda-demo")));

		// Sin email verificado no hay login (RF-AUTH-07), pero el enrolamiento sí puede.
		assertThrows(ConflictoException.class,
				() -> authService.login(new LoginRequest(email, "Secreta123")));
		verificarEmail(email);

		String qr = authService.iniciarEnrolamiento(new CredencialesRequest(email, "Secreta123"));
		assertTrue(qr.startsWith("otpauth://totp/"));

		Usuario u = usuarios.findByEmail(email).orElseThrow();
		int codigo = google.getTotpPassword(cryptoService.descifrar(u.getTotpSecret()));
		var conf = authService.confirmarEnrolamiento(new ConfirmarTotpRequest(email, "Secreta123", String.format("%06d", codigo)));
		assertTrue(conf.habilitado());
		assertEquals(10, conf.backupCodes().size());

		assertTrue(authService.login(new LoginRequest(email, "Secreta123")).requiereTotp());
		assertThrows(NoAutorizadoException.class,
				() -> authService.login(new LoginRequest(email, "mala")));

		int codigo2 = google.getTotpPassword(cryptoService.descifrar(
				usuarios.findByEmail(email).orElseThrow().getTotpSecret()));
		SesionEmitida s1 = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", String.format("%06d", codigo2), null), null, null);
		assertNotNull(s1.accessToken());
		assertFalse(s1.roles().isEmpty());
		jwtService.verificar(s1.accessToken());

		SesionEmitida s2 = authService.refresh(s1.refreshPlano(), null, null);
		assertNotEquals(s1.refreshPlano(), s2.refreshPlano());
		assertThrows(NoAutorizadoException.class, () -> authService.refresh(s1.refreshPlano(), null, null));

		authService.logout(s2.refreshPlano());
		assertThrows(NoAutorizadoException.class, () -> authService.refresh(s2.refreshPlano(), null, null));

		// Backup code como segunda vía.
		assertTrue(authService.login(new LoginRequest(email, "Secreta123")).requiereTotp());
		SesionEmitida s3 = authService.verificarTotpLogin(
				new TotpLoginRequest(email, "Secreta123", null, conf.backupCodes().get(0)), null, null);
		assertNotNull(s3.accessToken());
	}

	private void verificarEmail(String email) {
		Usuario u = usuarios.findByEmail(email).orElseThrow();
		var pendientes = tokens.findByUsuarioIdAndTipoAndUsadoEnIsNull(u.getId(),
				com.tienda.plataforma.auth.entity.TokenCuenta.VERIFICACION);
		assertFalse(pendientes.isEmpty());
		String plano = "flow-" + UUID.randomUUID();
		try {
			java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(plano.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : hash) {
				sb.append(String.format("%02x", b));
			}
			pendientes.get(pendientes.size() - 1).setTokenHash(sb.toString());
		} catch (Exception e) {
			throw new IllegalStateException("sha256", e);
		}
		tokens.save(pendientes.get(pendientes.size() - 1));
		authService.confirmarVerificacion(plano);
	}
}
