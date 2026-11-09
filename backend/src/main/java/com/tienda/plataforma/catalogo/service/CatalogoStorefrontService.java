package com.tienda.plataforma.catalogo.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.dto.ProductoCardResponse;
import com.tienda.plataforma.catalogo.dto.ProductoDetalleResponse;
import com.tienda.plataforma.catalogo.dto.ColeccionResponse;
import com.tienda.plataforma.catalogo.dto.EtiquetaResponse;
import com.tienda.plataforma.catalogo.dto.VarianteResponse;
import com.tienda.plataforma.catalogo.entity.ColeccionProducto;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ColeccionProductoRepository;
import com.tienda.plataforma.catalogo.repository.ColeccionRepository;
import com.tienda.plataforma.catalogo.repository.EtiquetaRepository;
import com.tienda.plataforma.catalogo.repository.ProductoEtiquetaRepository;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.dto.ResenaPublicaResponse;
import com.tienda.plataforma.venta.repository.ResenaRepository;

/**
 * RF-STORE-01: catálogo público por tienda. Solo productos activos con
 * variantes activas; el tenant lo fija `TenantFilter` desde el slug de ruta.
 */
@Service
public class CatalogoStorefrontService {

	private static final int LIMITE_DEFECTO = 20;
	private static final int LIMITE_MAXIMO = 50;

	private final ProductoRepository productos;
	private final ProductoVarianteRepository variantes;
	private final ResenaRepository resenas;
	private final ColeccionRepository colecciones;
	private final ColeccionProductoRepository asignaciones;
	private final EtiquetaRepository etiquetas;
	private final ProductoEtiquetaRepository etiquetados;

	public CatalogoStorefrontService(ProductoRepository productos,
			ProductoVarianteRepository variantes, ResenaRepository resenas,
			ColeccionRepository colecciones, ColeccionProductoRepository asignaciones,
			EtiquetaRepository etiquetas, ProductoEtiquetaRepository etiquetados) {
		this.productos = productos;
		this.variantes = variantes;
		this.resenas = resenas;
		this.colecciones = colecciones;
		this.asignaciones = asignaciones;
		this.etiquetas = etiquetas;
		this.etiquetados = etiquetados;
	}

	@Transactional(readOnly = true)
	public List<ProductoCardResponse> listar(Long tiendaId, String q, Integer limite) {
		return listar(tiendaId, q, null, limite);
	}

	@Transactional(readOnly = true)
	public List<ProductoCardResponse> listar(Long tiendaId, String q, String etiquetaSlug,
			Integer limite) {
		int tope = limite == null ? LIMITE_DEFECTO : Math.min(Math.max(limite, 1), LIMITE_MAXIMO);
		String filtro = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
		String tag = etiquetaSlug == null ? null : etiquetaSlug.trim();
		Long etiquetaId = null;
		if (tag != null && !tag.isEmpty()) {
			etiquetaId = etiquetas.findBySlug(tag).map(e -> e.getId()).orElse(null);
			if (etiquetaId == null) {
				return List.of();
			}
		}
		final Long filtroEtiqueta = etiquetaId;
		return productos.findByTiendaIdAndEstado(tiendaId, Producto.ACTIVO).stream()
				.filter(p -> filtro == null || filtro.isEmpty()
						|| p.getTitulo().toLowerCase(Locale.ROOT).contains(filtro))
				.filter(p -> filtroEtiqueta == null || etiquetados
						.findByProductoIdAndEtiquetaId(p.getId(), filtroEtiqueta).isPresent())
				.sorted(Comparator.comparing(Producto::getId))
				.limit(tope)
				.map(p -> new ProductoCardResponse(p.getId(), p.getTitulo(), p.getSlug(),
						precioDesde(p.getId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public ProductoDetalleResponse detalle(Long tiendaId, String slugProducto) {
		Producto producto = productos.findBySlug(slugProducto)
				.filter(p -> p.getTienda().getId().equals(tiendaId)
						&& Producto.ACTIVO.equals(p.getEstado()))
				.orElseThrow(() -> new NoEncontradoException("producto"));
		List<VarianteResponse> activas = variantes.findByProductoId(producto.getId()).stream()
				.filter(v -> Boolean.TRUE.equals(v.getActivo()))
				.sorted(Comparator.comparing(ProductoVariante::getId))
				.map(VarianteResponse::de)
				.toList();
		List<String> tags = etiquetados.findByProductoId(producto.getId()).stream()
				.map(fila -> fila.getEtiqueta().getSlug())
				.sorted()
				.toList();
		return new ProductoDetalleResponse(producto.getId(), producto.getTitulo(),
				producto.getSlug(), producto.getDescripcion(), activas, tags);
	}

	/** RF-REV-01: públicamente solo reseñas aprobadas, sin datos del autor. */
	@Transactional(readOnly = true)
	public List<ResenaPublicaResponse> resenas(Long tiendaId, String slugProducto) {
		Producto producto = productos.findBySlug(slugProducto)
				.filter(p -> p.getTienda().getId().equals(tiendaId)
						&& Producto.ACTIVO.equals(p.getEstado()))
				.orElseThrow(() -> new NoEncontradoException("producto"));
		return resenas.findByProductoIdAndAprobadaTrue(producto.getId()).stream()
				.sorted(Comparator.comparing(r -> r.getId()))
				.map(r -> new ResenaPublicaResponse(r.getId(), r.getCalificacion(),
						r.getTitulo(), r.getComentario()))
				.toList();
	}

	/** PLP por colección: solo activas con al menos un producto activo de la tienda. */
	@Transactional(readOnly = true)
	public List<ColeccionResponse> colecciones(Long tiendaId) {
		return colecciones.findByActivoTrueOrderByIdAsc().stream()
				.filter(c -> asignaciones.findByColeccionIdOrderByPosicionAsc(c.getId()).stream()
						.map(ColeccionProducto::getProducto)
						.anyMatch(p -> p.getTienda().getId().equals(tiendaId)
								&& Producto.ACTIVO.equals(p.getEstado())))
				.map(c -> new ColeccionResponse(c.getId(), c.getTitulo(), c.getSlug(),
						c.getDescripcion(), c.getActivo()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ProductoCardResponse> productosDeColeccion(Long tiendaId, String slugColeccion) {		var coleccion = colecciones.findBySlug(slugColeccion)
				.filter(c -> Boolean.TRUE.equals(c.getActivo()))
				.orElseThrow(() -> new NoEncontradoException("colección"));
		return asignaciones.findByColeccionIdOrderByPosicionAsc(coleccion.getId()).stream()
				.map(ColeccionProducto::getProducto)
				.filter(p -> p.getTienda().getId().equals(tiendaId)
						&& Producto.ACTIVO.equals(p.getEstado()))
				.map(p -> new ProductoCardResponse(p.getId(), p.getTitulo(), p.getSlug(),
						precioDesde(p.getId())))
				.toList();
	}

	/** Etiquetas usadas por productos activos de la tienda, ordenadas. */
	@Transactional(readOnly = true)
	public List<EtiquetaResponse> etiquetas(Long tiendaId) {
		return productos.findByTiendaIdAndEstado(tiendaId, Producto.ACTIVO).stream()
				.flatMap(p -> etiquetados.findByProductoId(p.getId()).stream())
				.map(fila -> fila.getEtiqueta())
				.collect(java.util.stream.Collectors.toMap(
						e -> e.getId(), e -> e, (a, b) -> a))
				.values().stream()
				.sorted(Comparator.comparing(e -> e.getId()))
				.map(e -> new EtiquetaResponse(e.getId(), e.getNombre(), e.getSlug()))
				.toList();
	}

	private BigDecimal precioDesde(Long productoId) {		return variantes.findByProductoId(productoId).stream()
				.filter(v -> Boolean.TRUE.equals(v.getActivo()) && v.getPrecio() != null)
				.map(ProductoVariante::getPrecio)
				.min(Comparator.naturalOrder())
				.orElse(null);
	}
}
