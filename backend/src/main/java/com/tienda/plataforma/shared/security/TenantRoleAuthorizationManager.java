package com.tienda.plataforma.shared.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** Autorización de superficies a partir del rol vigente en el tenant del request (V12). */
@Component
public class TenantRoleAuthorizationManager {

	private final TiendaUsuarioRepository tiendaUsuarios;
	private final ClienteRepository clientes;

	public TenantRoleAuthorizationManager(TiendaUsuarioRepository tiendaUsuarios, ClienteRepository clientes) {
		this.tiendaUsuarios = tiendaUsuarios;
		this.clientes = clientes;
	}

	/** El rol admin/operador vive en tienda_usuarios, no en el JWT global. */
	public AuthorizationManager<RequestAuthorizationContext> adminOOperadorDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || esSuperAdmin(actor)) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId)
					.map(v -> TiendaUsuario.ADMIN.equals(v.getRolTienda())
							|| TiendaUsuario.OPERADOR.equals(v.getRolTienda()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	/** Acciones reservadas al propietario/admin, por ejemplo resetear el 2FA de la tienda. */
	public AuthorizationManager<RequestAuthorizationContext> adminDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || esSuperAdmin(actor)) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId)
					.map(v -> TiendaUsuario.ADMIN.equals(v.getRolTienda()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	/** El cliente se deriva de clientes.tienda_id, no de una autoridad global del JWT. */
	public AuthorizationManager<RequestAuthorizationContext> clienteDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || !tieneAutoridad(actor, "ROLE_cliente")
					|| esSuperAdmin(actor) || tieneAutoridad(actor, "ROLE_admin")
					|| tieneAutoridad(actor, "ROLE_operador")) {
				return new AuthorizationDecision(false);
			}

			// Un miembro admin/operador de esta tienda no puede comprar en su propia tienda.
			if (tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId).isPresent()) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = clientes.findByUsuarioId(usuarioId)
					.map(c -> c.getTienda().getId().equals(tienda.getId()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	private Long usuarioId(Authentication actor) {
		if (actor == null || !actor.isAuthenticated()) {
			return null;
		}
		return actor.getPrincipal() instanceof Long id ? id : null;
	}

	private boolean esSuperAdmin(Authentication actor) {
		return tieneAutoridad(actor, "ROLE_super_admin");
	}

	private boolean tieneAutoridad(Authentication actor, String autoridad) {
		return actor != null && actor.getAuthorities().stream()
				.anyMatch(a -> autoridad.equals(a.getAuthority()));
	}
}
