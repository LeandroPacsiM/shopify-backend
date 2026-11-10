package com.tienda.plataforma.shared.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Valida el JWT Bearer y fija la autenticación con roles. No resuelve tienda (eso es TenantFilter). */
@Component
public class JwtFilter extends OncePerRequestFilter {

	private final JwtService jwtService;

	public JwtFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String ruta = request.getRequestURI();
		if (ruta.startsWith("/api/webhooks/") || ruta.startsWith("/swagger-ui")
				|| ruta.startsWith("/v3/api-docs")) {
			return true;
		}
		return ruta.startsWith("/auth/") && !ruta.startsWith("/auth/tiendas");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String auth = request.getHeader("Authorization");
		if (auth != null && auth.startsWith("Bearer ")) {
			try {
				// Solo toca tokens staff (aud exacta); los CUSTOMER los
				// atiende ClienteJwtFilter. Nunca limpia un principal ajeno.
				DecodedJWT sinVerificar = com.auth0.jwt.JWT.decode(auth.substring(7));
				if (sinVerificar.getAudience().contains(JwtService.AUD_STAFF)) {
					DecodedJWT jwt = jwtService.verificarStaff(auth.substring(7));
					Long usuarioId = Long.valueOf(jwt.getSubject());
					List<SimpleGrantedAuthority> autoridades = jwt.getClaim("roles").asList(String.class).stream()
							.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
							.toList();
					var authentication = new UsernamePasswordAuthenticationToken(usuarioId, null, autoridades);
					SecurityContextHolder.getContext().setAuthentication(authentication);
					request.setAttribute("usuarioId", usuarioId);
				}
			} catch (Exception e) {
				SecurityContextHolder.clearContext();
			}
		}
		try {
			chain.doFilter(request, response);
		} finally {
			SecurityContextHolder.clearContext();
			TenantContext.limpiar();
		}
	}
}
