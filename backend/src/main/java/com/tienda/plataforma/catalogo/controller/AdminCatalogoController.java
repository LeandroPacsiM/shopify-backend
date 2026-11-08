package com.tienda.plataforma.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.catalogo.dto.CrearProductoRequest;
import com.tienda.plataforma.catalogo.dto.CrearVarianteRequest;
import com.tienda.plataforma.catalogo.dto.ProductoResponse;
import com.tienda.plataforma.catalogo.dto.VarianteResponse;
import com.tienda.plataforma.catalogo.service.ProductoService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: catálogo (RF-ADMIN-01). Tienda por TenantFilter. */
@RestController
@RequestMapping("/api/admin/productos")
@Tag(name = "Admin catálogo", description = "Productos y variantes de la tienda")
public class AdminCatalogoController {

	private final ProductoService productoService;

	public AdminCatalogoController(ProductoService productoService) {
		this.productoService = productoService;
	}

	@GetMapping
	@Operation(summary = "Lista productos activos de la tienda")
	public List<ProductoResponse> listar() {
		return productoService.listar(tiendaId());
	}

	@PostMapping
	@Operation(summary = "Crea un producto en borrador")
	public ProductoResponse crear(@Valid @RequestBody CrearProductoRequest req) {
		return productoService.crear(tiendaId(), req);
	}

	@PatchMapping("/{id}/publicar")
	@Operation(summary = "Publica un producto")
	public ProductoResponse publicar(@PathVariable Long id) {
		return productoService.publicar(tiendaId(), id);
	}

	@PatchMapping("/{id}/archivar")
	@Operation(summary = "Archiva un producto (no lo borra)")
	public ProductoResponse archivar(@PathVariable Long id) {
		return productoService.archivar(tiendaId(), id);
	}

	@GetMapping("/{id}/variantes")
	@Operation(summary = "Lista variantes del producto")
	public List<VarianteResponse> variantes(@PathVariable Long id) {
		return productoService.variantes(tiendaId(), id);
	}

	@PostMapping("/{id}/variantes")
	@Operation(summary = "Crea una variante comprable")
	public VarianteResponse crearVariante(@PathVariable Long id, @Valid @RequestBody CrearVarianteRequest req) {
		return productoService.crearVariante(tiendaId(), id, req);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
