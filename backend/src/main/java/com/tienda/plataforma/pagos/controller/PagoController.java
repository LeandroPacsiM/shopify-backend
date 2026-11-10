package com.tienda.plataforma.pagos.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.pagos.dto.CrearIntentoRequest;
import com.tienda.plataforma.pagos.dto.PagoResponse;
import com.tienda.plataforma.pagos.service.PagoService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.ClientePrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Checkout: el cliente paga su pedido (RF-PAY-01/02). */
@RestController
@RequestMapping("/api/checkout/pedidos/{pedidoId}/pagos")
@Tag(name = "Pagos", description = "Intentos de pago del checkout")
public class PagoController {

	private final PagoService pagoService;

	public PagoController(PagoService pagoService) {
		this.pagoService = pagoService;
	}

	@PostMapping
	@Operation(summary = "Crea un intento de cobro por el saldo restante")
	public PagoResponse crear(@PathVariable Long pedidoId, @Valid @RequestBody CrearIntentoRequest req) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof ClientePrincipal p)) {
			throw new NoAutorizadoException("se requiere sesión CUSTOMER");
		}
		return pagoService.crearIntento(p.clienteId(), pedidoId, req.pasarela(), req.idempotencia());
	}
}
