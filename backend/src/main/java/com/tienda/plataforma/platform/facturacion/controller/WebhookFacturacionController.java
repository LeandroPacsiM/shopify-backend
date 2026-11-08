package com.tienda.plataforma.platform.facturacion.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.platform.facturacion.dto.FacturaWebhookRequest;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Webhook de facturación de plataforma: sin JWT, con firma (RF-BILL). */
@RestController
@RequestMapping("/api/webhooks/facturacion")
@Tag(name = "Webhooks", description = "Eventos de suscripción (idempotentes)")
public class WebhookFacturacionController {

	private final FacturacionService facturacion;

	public WebhookFacturacionController(FacturacionService facturacion) {
		this.facturacion = facturacion;
	}

	@PostMapping
	@Operation(summary = "Procesa un evento de suscripción (deduplica por event-id)")
	public void recibir(@Valid @RequestBody FacturaWebhookRequest req) {
		facturacion.webhook(req);
	}

	@PostMapping("/stripe")
	@Operation(summary = "Evento crudo de Stripe (Stripe-Signature + JSON tal cual)")
	public void recibirStripe(@RequestBody String cuerpo,
			@RequestHeader("Stripe-Signature") String firma) {
		facturacion.webhookStripe(cuerpo, firma);
	}
}
