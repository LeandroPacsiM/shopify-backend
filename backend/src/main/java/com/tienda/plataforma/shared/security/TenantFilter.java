package com.tienda.plataforma.shared.security;

import java.io.IOException;

import org.springframework.http.HttpMethod;
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
 * Resuelve y fija el tenant antes de autorizar la ruta (RF-TENANT-02).
 * Storefront usa el slug de ruta y, si también llega X-Tienda-Slug, ambos
 * deben coincidir. Admin y checkout requieren el header. Las rutas públicas
 * de Storefront no requieren JWT; su servicio valida el token y la titularidad
 * de los recursos. Una tienda congelada solo conserva acceso a facturación.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

	private static final String STOREFRONT = "/api/storefront";
	private static final String FACTURACION = "/api/admin/facturacion";

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
		String ruta = ruta(request);
		return dentroDe(ruta, "/auth") || dentroDe(ruta, "/api/webhooks")
				|| dentroDe(ruta, "/swagger-ui") || dentroDe(ruta, "/v3/api-docs")
				|| !requiereTenant(ruta);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String ruta = ruta(request);
		String slugRuta = slugStorefront(ruta);
		String slugHeader = normalizar(request.getHeader("X-Tienda-Slug"));
		if (ruta.startsWith(STOREFRONT + "/") && slugRuta == null) {
			prohibido(response);
			return;
		}
		if (slugRuta != null && slugHeader != null && !slugRuta.equals(slugHeader)) {
			prohibido(response);
			return;
		}

		// En Storefront el slug de la ruta es la fuente; admin/checkout no tienen slug.
		String slug = slugRuta == null ? slugHeader : slugRuta;
		if (slug == null) {
			prohibido(response);
			return;
		}

		Tienda tienda = tiendas.findBySlug(slug).orElse(null);
		if (tienda == null) {
			prohibido(response);
			return;
		}

		// Catálogo/carrito son públicos (el carrito por token); las rutas privadas
		// de Storefront, admin y checkout exigen vínculo con la tienda.
		if (!storefrontPublico(ruta, request.getMethod()) && !vinculadoA(tienda)) {
			prohibido(response);
			return;
		}
		if (Boolean.FALSE.equals(tienda.getActiva()) && !esFacturacion(ruta)) {
			prohibido(response);
			return;
		}

		TenantContext.fijar(tienda);
		try {
			chain.doFilter(request, response);
		} finally {
			TenantContext.limpiar();
		}
	}

	private boolean requiereTenant(String ruta) {
		return dentroDe(ruta, "/api/admin") || dentroDe(ruta, "/api/checkout")
				|| dentroDe(ruta, STOREFRONT);
	}

	private boolean vinculadoA(Tienda tienda) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof Long usuarioId)) {
			return false;
		}
		boolean global = auth.getAuthorities().stream()
				.anyMatch(a -> "ROLE_super_admin".equals(a.getAuthority()));
		return global || tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId).isPresent()
				|| clientes.findByUsuarioId(usuarioId)
						.map(c -> c.getTienda().getId().equals(tienda.getId()))
						.orElse(false);
	}

	private boolean esFacturacion(String ruta) {
		return dentroDe(ruta, FACTURACION);
	}

	private boolean storefrontPublico(String ruta, String metodo) {
		String slug = slugStorefront(ruta);
		if (slug == null) {
			return false;
		}
		String prefijo = STOREFRONT + "/" + slug;
		String recurso = ruta.substring(prefijo.length());
		boolean carrito = recurso.equals("/carrito") || recurso.startsWith("/carrito/");
		boolean catalogo = HttpMethod.GET.matches(metodo)
				&& (recurso.equals("/productos") || recurso.startsWith("/productos/")
						|| recurso.equals("/colecciones") || recurso.startsWith("/colecciones/")
						|| recurso.equals("/etiquetas"));
		return carrito || catalogo;
	}

	private String slugStorefront(String ruta) {
		String prefijo = STOREFRONT + "/";
		if (!ruta.startsWith(prefijo)) {
			return null;
		}
		String resto = ruta.substring(prefijo.length());
		int separador = resto.indexOf('/');
		String slug = separador < 0 ? resto : resto.substring(0, separador);
		return normalizar(slug);
	}

	private String ruta(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String contexto = request.getContextPath();
		return contexto == null || contexto.isEmpty() ? uri : uri.substring(contexto.length());
	}

	private String normalizar(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		return valor.trim();
	}

	private boolean dentroDe(String ruta, String prefijo) {
		return ruta.equals(prefijo) || ruta.startsWith(prefijo + "/");
	}

	private void prohibido(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json");
		response.getWriter().write("{\"codigo\":403,\"mensaje\":\"sin acceso a la tienda\"}");
	}
}
