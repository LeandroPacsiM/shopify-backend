package com.tienda.plataforma.realtime;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.tienda.plataforma.shared.security.JwtService;

/** Handshake: exige JWT (header Authorization o ?token=) y guarda usuarioId+roles. */
@Component
public class WsAuthInterceptor implements HandshakeInterceptor {

	private final JwtService jwtService;

	public WsAuthInterceptor(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		String token = null;
		for (String h : request.getHeaders().getOrEmpty("Authorization")) {
			if (h.startsWith("Bearer ")) {
				token = h.substring(7);
			}
		}
		if (token == null) {
			String query = request.getURI().getQuery();
			if (query != null) {
				for (String par : query.split("&")) {
					if (par.startsWith("token=")) {
						token = par.substring(6);
					}
				}
			}
		}
		if (token == null) {
			return false;
		}
		try {
			DecodedJWT jwt = jwtService.verificar(token);
			attributes.put("usuarioId", Long.valueOf(jwt.getSubject()));
			attributes.put("roles", jwt.getClaim("roles").asList(String.class));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {
	}
}
