package com.tienda.plataforma.venta.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.ClientePrincipal;
import com.tienda.plataforma.venta.dto.AgregarLineaRequest;
import com.tienda.plataforma.venta.dto.CarritoResponse;
import com.tienda.plataforma.venta.dto.TrackingResponse;
import com.tienda.plataforma.venta.service.CarritoService;
import com.tienda.plataforma.venta.service.TrackingService;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Superficie storefront: datos del propio cliente (RF-STORE-02, RF-SHIP-02). */
@RestController
@RequestMapping("/api/storefront/{slug}")
@Tag(name = "Storefront", description = "Catálogo, carrito y cuenta del cliente por tienda")
public class StorefrontController {

	private final TrackingService trackingService;
	private final CarritoService carritoService;

	public StorefrontController(TrackingService trackingService, CarritoService carritoService) {
		this.trackingService = trackingService;
		this.carritoService = carritoService;
	}

	@GetMapping("/cuenta/pedidos/{id}/tracking")
	@Operation(summary = "Tracking de un pedido propio, eventos ordenados")
	public TrackingResponse tracking(@PathVariable String slug, @PathVariable Long id) {
		return trackingService.tracking(clienteId(), slug, id);
	}

	@PostMapping("/carrito")
	@Operation(summary = "Recupera o crea el carrito (anónimo o del cliente)")
	public CarritoResponse carrito(@PathVariable String slug,
			@RequestHeader(value = "X-Carrito-Token", required = false) String token) {
		return carritoService.obtenerOCrear(slug, clienteId(), token);
	}

	@PostMapping("/carrito/lineas")
	@Operation(summary = "Agrega una variante al carrito con precio vigente")
	public CarritoResponse agregarLinea(@PathVariable String slug,
			@RequestHeader("X-Carrito-Token") String token,
			@Valid @RequestBody AgregarLineaRequest req) {
		return carritoService.agregarLinea(slug, token, clienteId(), req);
	}

	private Long clienteId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication == null ? null : authentication.getPrincipal();
		if (principal instanceof ClientePrincipal p) {
			return p.clienteId();
		}
		if (principal instanceof Long) {
			throw new NoAutorizadoException("se requiere sesión CUSTOMER");
		}
		return null;
	}
}
