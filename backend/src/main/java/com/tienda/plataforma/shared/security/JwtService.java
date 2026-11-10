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

/** JWT de acceso corto (15 min). El refresh opaco lo guarda `sesiones`.
 * Audiencias disjuntas (ADR-016): `tienda-admin` (staff) y
 * `tienda-storefront` (CUSTOMER); ningún filtro acepta la ajena. */
@Service
public class JwtService {

	private static final long ACCESS_MINUTOS = 15;

	public static final String AUD_STAFF = "tienda-admin";
	public static final String AUD_CUSTOMER = "tienda-storefront";
	public static final String AMBITO_STAFF = "staff";
	public static final String AMBITO_CUSTOMER = "cliente";

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
				.withAudience(AUD_STAFF)
				.withClaim("ambito", AMBITO_STAFF)
				.withSubject(String.valueOf(usuarioId))
				.withArrayClaim("roles", roles.toArray(new String[0]))
				.withExpiresAt(Date.from(expira))
				.withIssuedAt(new Date())
				.sign(algoritmo);
	}

	/** Access CUSTOMER (15 min): `sub=cliente:{id}`, sin roles. */
	public String accesoCliente(Long clienteId, Long tiendaId) {
		Instant expira = Instant.now().plusSeconds(ACCESS_MINUTOS * 60);
		return JWT.create()
				.withIssuer(emisor)
				.withAudience(AUD_CUSTOMER)
				.withClaim("ambito", AMBITO_CUSTOMER)
				.withClaim("tienda_id", tiendaId)
				.withSubject("cliente:" + clienteId)
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

	public DecodedJWT verificarStaff(String token) {
		DecodedJWT jwt = verificar(token);
		if (!jwt.getAudience().contains(AUD_STAFF)
				|| !AMBITO_STAFF.equals(jwt.getClaim("ambito").asString())) {
			throw new com.tienda.plataforma.shared.exception.NoAutorizadoException("token inválido");
		}
		return jwt;
	}

	public DecodedJWT verificarCliente(String token) {
		DecodedJWT jwt = verificar(token);
		if (!jwt.getAudience().contains(AUD_CUSTOMER)
				|| !AMBITO_CUSTOMER.equals(jwt.getClaim("ambito").asString())
				|| jwt.getClaim("tienda_id").isNull()) {
			throw new com.tienda.plataforma.shared.exception.NoAutorizadoException("token inválido");
		}
		return jwt;
	}
}
