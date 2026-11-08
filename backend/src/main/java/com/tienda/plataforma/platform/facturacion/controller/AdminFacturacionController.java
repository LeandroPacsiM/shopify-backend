package com.tienda.plataforma.platform.facturacion.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.platform.facturacion.dto.SuscribirRequest;
import com.tienda.plataforma.platform.facturacion.dto.SuscripcionResponse;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Facturación de la tienda con la plataforma (RF-BILL, RF-ADMIN-05). */
@RestController
@RequestMapping("/api/admin/facturacion")
@Tag(name = "Admin facturación", description = "Suscripción de la tienda al software")
public class AdminFacturacionController {

	private final FacturacionService facturacion;

	public AdminFacturacionController(FacturacionService facturacion) {
		this.facturacion = facturacion;
	}

	@GetMapping
	@Operation(summary = "Ve la suscripción de la tienda")
	public SuscripcionResponse ver() {
		return facturacion.ver(tiendaId());
	}

	@PostMapping("/suscribir")
	@Operation(summary = "Crea o cambia el plan (pendiente hasta el primer pago)")
	public SuscripcionResponse suscribir(@Valid @RequestBody SuscribirRequest req) {
		return facturacion.suscribir(tiendaId(), req.plan());
	}

	@PostMapping("/cancelar")
	@Operation(summary = "Cancela la suscripción de la tienda")
	public SuscripcionResponse cancelar() {
		return facturacion.cancelar(tiendaId());
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
