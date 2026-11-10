package com.tienda.plataforma.shared.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** Autorización de superficies a partir del rol vigente en el tenant del request (V12 + ADR-016). */
@Component
public class TenantRoleAuthorizationManager {

	private final TiendaUsuarioRepository tiendaUsuarios;

	public TenantRoleAuthorizationManager(TiendaUsuarioRepository tiendaUsuarios) {
		this.tiendaUsuarios = tiendaUsuarios;
	}

	/** El rol admin/operador vive en tienda_usuarios, no en el JWT global. El OWNER conserva todos los poderes de admin. */
	public AuthorizationManager<RequestAuthorizationContext> adminOOperadorDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || esSuperAdmin(actor)) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId)
					.map(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda())
							|| TiendaUsuario.ADMIN.equals(v.getRolTienda())
							|| TiendaUsuario.OPERADOR.equals(v.getRolTienda()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	/** Acciones reservadas al admin (y al OWNER, que lo incluye todo), por ejemplo resetear el 2FA. */
	public AuthorizationManager<RequestAuthorizationContext> adminDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || esSuperAdmin(actor)) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId)
					.map(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda())
							|| TiendaUsuario.ADMIN.equals(v.getRolTienda()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	/** Acciones reservadas al STORE_OWNER, p. ej. transferir la propiedad. */
	public AuthorizationManager<RequestAuthorizationContext> ownerDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			Long usuarioId = usuarioId(actor);
			Tienda tienda = TenantContext.actual();
			if (usuarioId == null || tienda == null || esSuperAdmin(actor)) {
				return new AuthorizationDecision(false);
			}
			boolean autorizado = tiendaUsuarios.findByTiendaIdAndUsuarioId(tienda.getId(), usuarioId)
					.map(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda()))
					.orElse(false);
			return new AuthorizationDecision(autorizado);
		};
	}

	/**
	 * CUSTOMER por claim firmado (objetivo B, ADR-016): solo acepta
	 * `ClientePrincipal` cuya tienda coincide con el tenant. Un JWT staff
	 * (principal `Long`) jamás autoriza aquí, y viceversa.
	 */
	public AuthorizationManager<RequestAuthorizationContext> clienteDeTienda() {
		return (autenticacion, contexto) -> {
			Authentication actor = autenticacion.get();
			if (actor == null) {
				return new AuthorizationDecision(false);
			}
			Tienda tienda = TenantContext.actual();
			boolean autorizado = tienda != null
					&& actor.getPrincipal() instanceof ClientePrincipal p
					&& p.tiendaId().equals(tienda.getId());
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
