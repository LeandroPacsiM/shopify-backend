package com.tienda.plataforma.shared.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resuelve la tienda del request (RF-TENANT-02).
 * Fuente: header X-Tienda-Slug (los controladores con :slug en path lo
 * confirmarán contra este valor). Verifica vínculo usuario-tienda:
 * super_admin global, admin/operador por tienda_usuarios, cliente por clientes.
 * Sin vínculo → 403 sin filtrar existencia. Tienda inactiva (congelada por
 * facturación/compliance) → 403 salvo su facturación, que sigue accesible
 * para pagar y reactivar (RF-BILL).
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository tiendaUsuarios;
	private final ClienteRepository clientes;

	public TenantFilter(TiendaRepository tiendas, TiendaUsuarioRepository tiendaUsuarios,
			ClienteRepository clientes) {
		this.tiendas = tiendas;
		this.tiendaUsuarios = tiendaUsuarios;
		this.clientes = clientes;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String ruta = request.getRequestURI();
		return ruta.startsWith("/auth/") || ruta.startsWith("/api/webhooks/")
				|| ruta.startsWith("/swagger-ui") || ruta.startsWith("/v3/api-docs");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String slug = request.getHeader("X-Tienda-Slug");
		if (slug == null || slug.isBlank()) {
			chain.doFilter(request, response);
			return;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof Long usuarioId)) {
			prohibido(response);
			return;
		}
		boolean global = auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_super_admin"));
		Tienda tienda = tiendas.findBySlug(slug).orElse(null);
		boolean vinculado = tienda != null && (global
				|| tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId).isPresent()
				|| clientes.findByUsuarioId(usuarioId)
						.map(c -> c.getTienda().getId().equals(tienda.getId()))
						.orElse(false));
		if (!vinculado) {
			prohibido(response);
			return;
		}
		if (Boolean.FALSE.equals(tienda.getActiva())
				&& !request.getRequestURI().startsWith("/api/admin/facturacion")) {
			prohibido(response);
			return;
		}
		TenantContext.fijar(tienda);
		chain.doFilter(request, response);
	}

	private void prohibido(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json");
		response.getWriter().write("{\"codigo\":403,\"mensaje\":\"sin acceso a la tienda\"}");
	}
}
