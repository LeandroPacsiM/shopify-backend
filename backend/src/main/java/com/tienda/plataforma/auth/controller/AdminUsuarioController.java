package com.tienda.plataforma.auth.controller;

import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;

import com.tienda.plataforma.auth.dto.AgregarMiembroRequest;
import com.tienda.plataforma.auth.dto.CambiarRolRequest;
import com.tienda.plataforma.auth.dto.MiembroResponse;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.tenants.service.TiendaEquipoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: equipo y reseteo de 2FA (RF-ADMIN-04, RF-AUTH-06). Solo `admin`. */
@RestController
@RequestMapping("/api/admin/usuarios")
@Tag(name = "Admin usuarios", description = "Equipo de la tienda y reseteo de 2FA")
public class AdminUsuarioController {

	private final AuthService authService;
	private final TiendaEquipoService equipo;

	public AdminUsuarioController(AuthService authService, TiendaEquipoService equipo) {
		this.authService = authService;
		this.equipo = equipo;
	}

	@GetMapping
	@Operation(summary = "Lista admin y operadores de la tienda")
	public List<MiembroResponse> listar() {
		return equipo.listar(tiendaId());
	}

	@PostMapping
	@Operation(summary = "Agrega un usuario registrado a la tienda con un rol")
	public MiembroResponse agregar(@Valid @RequestBody AgregarMiembroRequest req) {
		return equipo.agregar(tiendaId(), req);
	}

	@PatchMapping("/{id}/rol")
	@Operation(summary = "Cambia el rol de un miembro (la tienda conserva un admin)")
	public MiembroResponse cambiarRol(@PathVariable Long id, @Valid @RequestBody CambiarRolRequest req) {
		return equipo.cambiarRol(tiendaId(), id, req.rol());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoca el acceso sin borrar el usuario")
	public void revocar(@PathVariable Long id) {
		equipo.revocar(tiendaId(), id);
	}

	@PostMapping("/{id}/2fa/reset")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Resetea el 2FA de un usuario vinculado a la tienda")
	public void resetTotp(@PathVariable Long id) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication == null ? null : authentication.getPrincipal();
		if (!(principal instanceof Long actorId)) {
			throw new NoAutorizadoException("sin autenticación");
		}
		Long tiendaId = TenantContext.actual() == null ? null : TenantContext.actual().getId();
		if (tiendaId == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		authService.resetTotpPorAdmin(actorId, id, tiendaId);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
