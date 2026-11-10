package com.tienda.plataforma.shared.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Valida el JWT CUSTOMER (`aud=tienda-storefront`) y fija un
 * `ClientePrincipal`. Ignora cualquier otro token (incluido el staff):
 * las audiencias nunca se intercambian. Inerte con flag off.
 */
@Component
public class ClienteJwtFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final boolean habilitado;

	public ClienteJwtFilter(JwtService jwtService,
			@Value("${auth.customer.enabled:false}") boolean habilitado) {
		this.jwtService = jwtService;
		this.habilitado = habilitado;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		if (habilitado) {
			String auth = request.getHeader("Authorization");
			if (auth != null && auth.startsWith("Bearer ")) {
				try {
					DecodedJWT sinVerificar = JWT.decode(auth.substring(7));
					if (sinVerificar.getAudience().contains(JwtService.AUD_CUSTOMER)) {
						DecodedJWT jwt = jwtService.verificarCliente(auth.substring(7));
						String sub = jwt.getSubject();
						Long clienteId = Long.valueOf(sub.substring("cliente:".length()));
						Long tiendaId = jwt.getClaim("tienda_id").asLong();
						var authentication = new UsernamePasswordAuthenticationToken(
								new ClientePrincipal(clienteId, tiendaId), null,
								List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
						SecurityContextHolder.getContext().setAuthentication(authentication);
						request.setAttribute("clienteId", clienteId);
						request.setAttribute("clienteTiendaId", tiendaId);
					}
				} catch (Exception e) {
					SecurityContextHolder.clearContext();
				}
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
