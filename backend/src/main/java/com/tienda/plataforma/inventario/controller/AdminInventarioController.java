package com.tienda.plataforma.inventario.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.inventario.dto.CrearUbicacionRequest;
import com.tienda.plataforma.inventario.dto.MovimientoStockRequest;
import com.tienda.plataforma.inventario.dto.NivelResponse;
import com.tienda.plataforma.inventario.dto.UbicacionResponse;
import com.tienda.plataforma.inventario.service.InventarioAdminService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: ubicaciones y stock (RF-ADMIN-02). Tienda por TenantFilter. */
@RestController
@RequestMapping("/api/admin/inventario")
@Tag(name = "Admin inventario", description = "Ubicaciones y movimientos de stock")
public class AdminInventarioController {

	private final InventarioAdminService inventario;

	public AdminInventarioController(InventarioAdminService inventario) {
		this.inventario = inventario;
	}

	@GetMapping("/ubicaciones")
	@Operation(summary = "Lista ubicaciones de la tienda")
	public List<UbicacionResponse> ubicaciones() {
		return inventario.ubicaciones(tiendaId());
	}

	@PostMapping("/ubicaciones")
	@Operation(summary = "Crea una ubicación activa")
	public UbicacionResponse crearUbicacion(@Valid @RequestBody CrearUbicacionRequest req) {
		return inventario.crearUbicacion(tiendaId(), req);
	}

	@GetMapping("/niveles")
	@Operation(summary = "Niveles de stock, opcionalmente por variante")
	public List<NivelResponse> niveles(@RequestParam(required = false) Long varianteId) {
		return inventario.niveles(tiendaId(), varianteId);
	}

	@PostMapping("/movimientos")
	@Operation(summary = "Entrada, salida o ajuste con bloqueo (reserva/liberacion son del pedido)")
	public NivelResponse mover(@Valid @RequestBody MovimientoStockRequest req) {
		return inventario.mover(tiendaId(), req);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
