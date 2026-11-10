package com.tienda.plataforma.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

/**
 * Fase 1 (ADR-017): claim `mfa` en el JWT staff. `acceso` emite completa,
 * `accesoLimitado` emite pendiente con roles vacíos; tokens legacy sin
 * claim se tratan como completos (compatibilidad 15 min).
 */
class JwtMfaTest {

	private static final String SECRETO = "test-secreto-largo-solo-para-tests-0123456789";

	private final JwtService jwtService = new JwtService(SECRETO);

	@Test
	void accesoEmiteMfaCompleta() {
		String token = jwtService.acceso(7L, List.of());
		DecodedJWT jwt = jwtService.verificarStaff(token);
		assertEquals("7", jwt.getSubject());
		assertEquals(JwtService.MFA_COMPLETA, jwt.getClaim(JwtService.CLAIM_MFA).asString());
		assertFalse(JwtService.esMfaPendiente(jwt));
	}

	@Test
	void accesoLimitadoEmiteMfaPendienteSinRoles() {
		String token = jwtService.accesoLimitado(9L);
		DecodedJWT jwt = jwtService.verificarStaff(token);
		assertEquals("9", jwt.getSubject());
		assertTrue(jwt.getAudience().contains(JwtService.AUD_STAFF));
		assertEquals(JwtService.AMBITO_STAFF, jwt.getClaim("ambito").asString());
		assertTrue(jwt.getClaim("roles").asList(String.class).isEmpty());
		assertEquals(JwtService.MFA_PENDIENTE, jwt.getClaim(JwtService.CLAIM_MFA).asString());
		assertTrue(JwtService.esMfaPendiente(jwt));
	}

	@Test
	void tokenLegacySinClaimSeTrataComoCompleto() {
		String legacy = JWT.create()
				.withIssuer("plataforma-tiendas")
				.withAudience(JwtService.AUD_STAFF)
				.withClaim("ambito", JwtService.AMBITO_STAFF)
				.withSubject("11")
				.withArrayClaim("roles", new String[0])
				.withExpiresAt(Date.from(Instant.now().plusSeconds(900)))
				.sign(Algorithm.HMAC256(SECRETO));
		DecodedJWT jwt = jwtService.verificarStaff(legacy);
		assertFalse(JwtService.esMfaPendiente(jwt));
	}

	@Test
	void customerNoTieneClaimMfa() {
		String token = jwtService.accesoCliente(3L, 5L);
		DecodedJWT jwt = jwtService.verificarCliente(token);
		assertFalse(JwtService.esMfaPendiente(jwt));
	}
}
