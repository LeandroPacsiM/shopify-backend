package com.tienda.plataforma.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;

import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Admin: reseteo de 2FA (RF-AUTH-06). Tienda por TenantFilter; super_admin global. */
@RestController
@RequestMapping("/api/admin/usuarios")
@Tag(name = "Admin usuarios", description = "Reseteo de 2FA")
public class AdminUsuarioController {

	private final AuthService authService;

	public AdminUsuarioController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/{id}/2fa/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Resetea el 2FA de un usuario vinculado a la tienda")
	public void resetTotp(@PathVariable Long id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean esSuper = auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_super_admin"));
		Long tiendaId = TenantContext.actual() == null ? null : TenantContext.actual().getId();
		if (!esSuper && tiendaId == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		authService.resetTotpPorAdmin(id, tiendaId, esSuper);
	}
}
