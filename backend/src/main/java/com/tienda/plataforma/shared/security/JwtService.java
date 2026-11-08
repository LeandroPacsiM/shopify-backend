package com.tienda.plataforma.shared.security;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

/** JWT de acceso corto (15 min). El refresh opaco lo guarda `sesiones`. */
@Service
public class JwtService {

	private static final long ACCESS_MINUTOS = 15;

	private final Algorithm algoritmo;
	private final String emisor = "plataforma-tiendas";

	public JwtService(@Value("${jwt.secret}") String secreto) {
		if (secreto == null || secreto.length() < 32) {
			throw new IllegalStateException("jwt.secret debe tener al menos 32 caracteres");
		}
		this.algoritmo = Algorithm.HMAC256(secreto);
	}

	public String acceso(Long usuarioId, List<String> roles) {
		Instant expira = Instant.now().plusSeconds(ACCESS_MINUTOS * 60);
		return JWT.create()
				.withIssuer(emisor)
				.withSubject(String.valueOf(usuarioId))
				.withArrayClaim("roles", roles.toArray(new String[0]))
				.withExpiresAt(Date.from(expira))
				.withIssuedAt(new Date())
				.sign(algoritmo);
	}

	public DecodedJWT verificar(String token) {
		try {
			return JWT.require(algoritmo).withIssuer(emisor).build().verify(token);
		} catch (JWTVerificationException e) {
			throw new com.tienda.plataforma.shared.exception.NoAutorizadoException("token inválido");
		}
	}
}
