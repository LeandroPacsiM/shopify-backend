package com.tienda.plataforma.venta.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.shared.security.ClientePrincipal;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.venta.dto.CheckoutResponse;
import com.tienda.plataforma.venta.dto.CrearCheckoutRequest;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.service.PedidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Checkout aislado: crear y confirmar (RF-CART-02/03). Tienda ya resuelta por TenantFilter. */
@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout", description = "Checkout con cuenta + 2FA y confirmación transaccional")
public class CheckoutController {

	private final PedidoService pedidoService;

	public CheckoutController(PedidoService pedidoService) {
		this.pedidoService = pedidoService;
	}

	@PostMapping("/orden")
	@Operation(summary = "Crea el checkout congelando totales")
	public CheckoutResponse crear(@Valid @RequestBody CrearCheckoutRequest req) {
		return pedidoService.crearCheckout(clienteId(), tiendaActual().getSlug(), req);
	}

	@PostMapping("/{id}/confirmar")
	@Operation(summary = "Confirma el pedido: snapshots, stock, historial (idempotente por checkout)")
	public PedidoResponse confirmar(@PathVariable Long id) {
		return pedidoService.confirmar(clienteId(), tiendaActual().getId(), id);
	}

	private Tienda tiendaActual() {
		Tienda tienda = TenantContext.actual();
		if (tienda == null) {
			throw new ProhibidoException("sin tienda activa");
		}
		return tienda;
	}

	private Long clienteId() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof ClientePrincipal p)) {
			throw new NoAutorizadoException("sin autenticación CUSTOMER");
		}
		return p.clienteId();
	}
}
