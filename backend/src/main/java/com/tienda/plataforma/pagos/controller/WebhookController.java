package com.tienda.plataforma.pagos.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.pagos.dto.WebhookPagoRequest;
import com.tienda.plataforma.pagos.service.PagoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Webhooks de pasarelas: sin JWT, con firma por proveedor (RF-PAY-02). */
@RestController
@RequestMapping("/api/webhooks/pagos")
@Tag(name = "Webhooks", description = "Notificaciones de pasarelas (idempotentes)")
public class WebhookController {

	private final PagoService pagoService;

	public WebhookController(PagoService pagoService) {
		this.pagoService = pagoService;
	}

	@PostMapping("/{codigo}")
	@Operation(summary = "Procesa un evento de pago (deduplica por event-id)")
	public void recibir(@PathVariable String codigo, @Valid @RequestBody WebhookPagoRequest req) {
		pagoService.webhook(codigo, req);
	}

	@PostMapping("/stripe")
	@Operation(summary = "Evento crudo de Stripe (Stripe-Signature + JSON tal cual)")
	public void recibirStripe(@RequestBody String cuerpo,
			@RequestHeader("Stripe-Signature") String firma) {
		pagoService.webhookStripe(cuerpo, firma);
	}
}
