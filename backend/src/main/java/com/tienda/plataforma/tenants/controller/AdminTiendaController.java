package com.tienda.plataforma.tenants.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.tenants.service.TiendaPropiedadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Tienda propia: transferencia de propiedad (ADR-016). Solo STORE_OWNER
 * (matcher `ownerDeTienda`); atómica bajo advisory lock por tienda.
 */
@RestController
@RequestMapping("/api/admin/tienda")
@Tag(name = "Admin tienda", description = "Propiedad de la tienda")
public class AdminTiendaController {

	public record TransferirRequest(@NotNull Long nuevoOwnerId) {
	}

	private final TiendaPropiedadService propiedad;

	public AdminTiendaController(TiendaPropiedadService propiedad) {
		this.propiedad = propiedad;
	}

	@PatchMapping("/propietario")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Transfiere la propiedad a otro miembro activo (atómica)")
	public void transferir(@Valid @RequestBody TransferirRequest req) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof Long actorId)) {
			throw new NoAutorizadoException("sin autenticación");
		}
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		propiedad.transferir(TenantContext.actual().getId(), actorId, req.nuevoOwnerId());
	}
}
