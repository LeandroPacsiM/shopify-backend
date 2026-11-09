package com.tienda.plataforma.venta.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.service.AdminPedidoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Admin de tienda: operar pedidos (RF-ADMIN-03, RF-ORDER-02). */
@RestController
@RequestMapping("/api/admin/pedidos")
@Tag(name = "Admin pedidos", description = "Listar, cancelar y archivar pedidos de la tienda")
public class AdminPedidoController {

	private final AdminPedidoService adminPedidoService;

	public AdminPedidoController(AdminPedidoService adminPedidoService) {
		this.adminPedidoService = adminPedidoService;
	}

	@GetMapping
	@Operation(summary = "Lista pedidos de la tienda, opcionalmente por estado (paginado)")
	public Page<PedidoResponse> listar(@RequestParam(required = false) String estado,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return adminPedidoService.listar(tiendaId(), estado, page, size);
	}

	@PatchMapping("/{id}/cancelar")
	@Operation(summary = "Cancela un pedido pendiente sin cobro; el pagado exige reembolso previo")
	public PedidoResponse cancelar(@PathVariable Long id) {
		return adminPedidoService.cancelar(tiendaId(), id);
	}

	@PatchMapping("/{id}/archivar")
	@Operation(summary = "Archiva o desarchiva un pedido")
	public PedidoResponse archivar(@PathVariable Long id) {
		return adminPedidoService.archivar(tiendaId(), id, true);
	}

	@PatchMapping("/{id}/desarchivar")
	@Operation(summary = "Desarchiva un pedido")
	public PedidoResponse desarchivar(@PathVariable Long id) {
		return adminPedidoService.archivar(tiendaId(), id, false);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
