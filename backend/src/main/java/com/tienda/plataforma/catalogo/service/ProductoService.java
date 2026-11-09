package com.tienda.plataforma.catalogo.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.dto.CrearProductoRequest;
import com.tienda.plataforma.catalogo.dto.CrearVarianteRequest;
import com.tienda.plataforma.catalogo.dto.ProductoResponse;
import com.tienda.plataforma.catalogo.dto.VarianteResponse;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** RF-ADMIN-01: catálogo de la tienda (Flujo 2 de 05, sin combinaciones ambiguas por ahora). */
@Service
public class ProductoService {

	private final ProductoRepository productos;
	private final ProductoVarianteRepository variantes;
	private final TiendaRepository tiendas;
	private final AuditoriaService auditoria;

	public ProductoService(ProductoRepository productos, ProductoVarianteRepository variantes,
			TiendaRepository tiendas, AuditoriaService auditoria) {
		this.productos = productos;
		this.variantes = variantes;
		this.tiendas = tiendas;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ProductoResponse> listar(Long tiendaId) {
		return productos.findByTiendaIdAndEstado(tiendaId, Producto.ACTIVO).stream()
				.map(ProductoResponse::de).toList();
	}

	@Transactional
	public ProductoResponse crear(Long tiendaId, CrearProductoRequest req) {
		Tienda tienda = tiendas.findById(tiendaId).orElseThrow(() -> new NoEncontradoException("tienda"));
		if (productos.findBySlug(req.slug()).isPresent()) {
			throw new ConflictoException("slug en uso");
		}
		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo(req.titulo());
		producto.setSlug(req.slug());
		producto.setDescripcion(req.descripcion());
		producto.setEstado(Producto.BORRADOR);
		producto.setCreadoEn(Instant.now());
		producto.setActualizadoEn(Instant.now());
		productos.save(producto);
		auditoria.registrar("productos", producto.getId(), "producto.crear");
		return ProductoResponse.de(producto);
	}

	@Transactional
	public ProductoResponse publicar(Long tiendaId, Long productoId) {
		Producto producto = deLaTienda(tiendaId, productoId);
		producto.setEstado(Producto.ACTIVO);
		producto.setPublicadoEn(Instant.now());
		producto.setActualizadoEn(Instant.now());
		productos.save(producto);
		auditoria.registrar("productos", producto.getId(), "producto.publicar");
		return ProductoResponse.de(producto);
	}

	@Transactional
	public ProductoResponse archivar(Long tiendaId, Long productoId) {
		Producto producto = deLaTienda(tiendaId, productoId);
		producto.setEstado(Producto.ARCHIVADO);
		producto.setActualizadoEn(Instant.now());
		productos.save(producto);
		auditoria.registrar("productos", producto.getId(), "producto.archivar");
		return ProductoResponse.de(producto);
	}

	@Transactional(readOnly = true)
	public List<VarianteResponse> variantes(Long tiendaId, Long productoId) {
		deLaTienda(tiendaId, productoId);
		return variantes.findByProductoId(productoId).stream().map(VarianteResponse::de).toList();
	}

	@Transactional
	public VarianteResponse crearVariante(Long tiendaId, Long productoId, CrearVarianteRequest req) {
		Producto producto = deLaTienda(tiendaId, productoId);
		if (req.sku() != null && variantes.findBySku(req.sku()).isPresent()) {
			throw new ConflictoException("sku en uso");
		}
		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku(req.sku());
		variante.setPrecio(req.precio());
		variante.setPrecioComparativo(req.precioComparativo());
		variante.setPesoGramos(req.pesoGramos());
		variante.setActivo(true);
		variante.setCreadoEn(Instant.now());
		variantes.save(variante);
		auditoria.registrar("producto_variantes", variante.getId(), "producto.crear_variante");
		return VarianteResponse.de(variante);
	}

	private Producto deLaTienda(Long tiendaId, Long productoId) {
		Producto producto = productos.findById(productoId)
				.orElseThrow(() -> new NoEncontradoException("producto"));
		if (!producto.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("producto");
		}
		return producto;
	}
}
