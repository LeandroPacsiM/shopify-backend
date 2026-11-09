package com.tienda.plataforma.envios.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.envios.dto.CrearEnvioRequest;
import com.tienda.plataforma.envios.dto.EnvioResponse;
import com.tienda.plataforma.envios.security.TrackingWebhookAuthenticator;
import com.tienda.plataforma.envios.service.EnvioService;
import com.tienda.plataforma.envios.service.TrackingWebhookService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: envíos; tracking sin JWT, autenticado con firma HMAC del transportista (RF-SHIP). */
@RestController
@Tag(name = "Admin envíos", description = "Despachos y tracking")
public class EnvioController {

	private final EnvioService envioService;
	private final TrackingWebhookService trackingWebhookService;

	public EnvioController(EnvioService envioService, TrackingWebhookService trackingWebhookService) {
		this.envioService = envioService;
		this.trackingWebhookService = trackingWebhookService;
	}

	@PostMapping("/api/admin/pedidos/{id}/envios")
	@Operation(summary = "Crea un envío parcial validando topes por línea")
	public EnvioResponse crear(@PathVariable Long id, @Valid @RequestBody CrearEnvioRequest req) {
		return envioService.crear(tiendaId(), id, req);
	}

	@PostMapping(path = "/api/webhooks/envios/tracking", consumes = "application/json")
	@Operation(summary = "Ingiere tracking con HMAC-SHA256 y tolerancia anti-replay de 5 minutos")
	public void tracking(
			@RequestHeader(name = TrackingWebhookAuthenticator.HEADER_TIMESTAMP, required = false) String timestamp,
			@RequestHeader(name = TrackingWebhookAuthenticator.HEADER_SIGNATURE, required = false) String firma,
			@RequestBody byte[] cuerpo) {
		trackingWebhookService.ingresar(cuerpo, timestamp, firma);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
