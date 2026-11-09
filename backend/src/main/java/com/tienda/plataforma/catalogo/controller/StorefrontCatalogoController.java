package com.tienda.plataforma.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.catalogo.dto.ProductoCardResponse;
import com.tienda.plataforma.catalogo.dto.ProductoDetalleResponse;
import com.tienda.plataforma.catalogo.dto.ColeccionResponse;
import com.tienda.plataforma.catalogo.dto.EtiquetaResponse;
import com.tienda.plataforma.catalogo.service.CatalogoStorefrontService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.venta.dto.ResenaPublicaResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Storefront público: catálogo de la tienda del slug (RF-STORE-01). Sin JWT. */
@RestController
@RequestMapping("/api/storefront/{slug}")
@Tag(name = "Storefront catálogo", description = "Productos activos con variantes activas")
public class StorefrontCatalogoController {

	private final CatalogoStorefrontService catalogo;

	public StorefrontCatalogoController(CatalogoStorefrontService catalogo) {
		this.catalogo = catalogo;
	}

	@GetMapping("/productos")
	@Operation(summary = "Lista productos activos de la tienda, opcionalmente con búsqueda o etiqueta")
	public List<ProductoCardResponse> listar(@PathVariable String slug,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String etiqueta,
			@RequestParam(required = false) Integer limit) {
		return catalogo.listar(tiendaId(), q, etiqueta, limit);
	}

	@GetMapping("/productos/{productoSlug}")
	@Operation(summary = "Ficha del producto con sus variantes activas")
	public ProductoDetalleResponse detalle(@PathVariable String slug,
			@PathVariable String productoSlug) {
		return catalogo.detalle(tiendaId(), productoSlug);
	}

	@GetMapping("/productos/{productoSlug}/resenas")
	@Operation(summary = "Reseñas aprobadas del producto")
	public List<ResenaPublicaResponse> resenas(@PathVariable String slug,
			@PathVariable String productoSlug) {
		return catalogo.resenas(tiendaId(), productoSlug);
	}

	@GetMapping("/colecciones")
	@Operation(summary = "Colecciones activas con productos de la tienda")
	public List<ColeccionResponse> colecciones(@PathVariable String slug) {
		return catalogo.colecciones(tiendaId());
	}

	@GetMapping("/colecciones/{coleccionSlug}/productos")
	@Operation(summary = "Productos activos de la tienda en la colección")
	public List<ProductoCardResponse> productosDeColeccion(@PathVariable String slug,
			@PathVariable String coleccionSlug) {
		return catalogo.productosDeColeccion(tiendaId(), coleccionSlug);
	}

	@GetMapping("/etiquetas")
	@Operation(summary = "Etiquetas usadas por productos activos de la tienda")
	public List<EtiquetaResponse> etiquetas(@PathVariable String slug) {
		return catalogo.etiquetas(tiendaId());
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
