package com.tienda.plataforma.envios.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.envios.dto.CrearEnvioRequest;
import com.tienda.plataforma.envios.dto.EnvioResponse;
import com.tienda.plataforma.envios.dto.TrackingWriteRequest;
import com.tienda.plataforma.envios.service.EnvioService;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: envíos; tracking de transportistas sin JWT (RF-SHIP). */
@RestController
@Tag(name = "Admin envíos", description = "Despachos y tracking")
public class EnvioController {

	private final EnvioService envioService;

	public EnvioController(EnvioService envioService) {
		this.envioService = envioService;
	}

	@PostMapping("/api/admin/pedidos/{id}/envios")
	@Operation(summary = "Crea un envío parcial validando topes por línea")
	public EnvioResponse crear(@PathVariable Long id, @Valid @RequestBody CrearEnvioRequest req) {
		return envioService.crear(tiendaId(), id, req);
	}

	@PostMapping("/api/webhooks/envios/tracking")
	@Operation(summary = "Ingiere un evento de tracking del transportista")
	public void tracking(@Valid @RequestBody TrackingWriteRequest req) {
		envioService.tracking(req);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
