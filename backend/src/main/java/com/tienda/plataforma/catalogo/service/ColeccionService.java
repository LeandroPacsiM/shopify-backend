package com.tienda.plataforma.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.dto.AsignarProductoRequest;
import com.tienda.plataforma.catalogo.dto.ColeccionResponse;
import com.tienda.plataforma.catalogo.dto.CrearColeccionRequest;
import com.tienda.plataforma.catalogo.dto.EditarColeccionRequest;
import com.tienda.plataforma.catalogo.entity.Coleccion;
import com.tienda.plataforma.catalogo.entity.ColeccionProducto;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.repository.ColeccionProductoRepository;
import com.tienda.plataforma.catalogo.repository.ColeccionRepository;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;

/**
 * RF-ADMIN-01: colecciones editoriales (globales) con productos de la tienda.
 * Solo se asignan productos propios; lo público filtra por tienda.
 */
@Service
public class ColeccionService {

	private final ColeccionRepository colecciones;
	private final ColeccionProductoRepository asignaciones;
	private final ProductoRepository productos;
	private final AuditoriaService auditoria;

	public ColeccionService(ColeccionRepository colecciones,
			ColeccionProductoRepository asignaciones, ProductoRepository productos,
			AuditoriaService auditoria) {
		this.colecciones = colecciones;
		this.asignaciones = asignaciones;
		this.productos = productos;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<ColeccionResponse> listar() {
		return colecciones.findAll().stream().sorted((a, b) -> a.getId().compareTo(b.getId()))
				.map(ColeccionResponse::de).toList();
	}

	@Transactional
	public ColeccionResponse crear(CrearColeccionRequest req) {
		if (colecciones.findBySlug(req.slug().trim()).isPresent()) {
			throw new ConflictoException("slug en uso");
		}
		Coleccion coleccion = new Coleccion();
		coleccion.setTitulo(req.titulo().trim());
		coleccion.setSlug(req.slug().trim());
		coleccion.setDescripcion(req.descripcion());
		coleccion.setActivo(true);
		colecciones.save(coleccion);
		auditoria.registrar("colecciones", coleccion.getId(), "coleccion.crear");
		return ColeccionResponse.de(coleccion);
	}

	@Transactional
	public ColeccionResponse editar(Long coleccionId, EditarColeccionRequest req) {
		Coleccion coleccion = colecciones.findById(coleccionId)
				.orElseThrow(() -> new NoEncontradoException("colección"));
		if (req.titulo() != null && !req.titulo().isBlank()) {
			coleccion.setTitulo(req.titulo().trim());
		}
		if (req.descripcion() != null) {
			coleccion.setDescripcion(req.descripcion());
		}
		if (req.activo() != null) {
			coleccion.setActivo(req.activo());
		}
		auditoria.registrar("colecciones", coleccionId, "coleccion.editar");
		return ColeccionResponse.de(coleccion);
	}

	@Transactional
	public void asignar(Long tiendaId, Long coleccionId, AsignarProductoRequest req) {
		Coleccion coleccion = colecciones.findById(coleccionId)
				.orElseThrow(() -> new NoEncontradoException("colección"));
		Producto producto = productos.findById(req.productoId())
				.orElseThrow(() -> new NoEncontradoException("producto"));
		if (!producto.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("producto");
		}
		if (asignaciones.findByColeccionIdAndProductoId(coleccion.getId(), producto.getId()).isPresent()) {
			throw new ConflictoException("producto ya está en la colección");
		}
		ColeccionProducto fila = new ColeccionProducto();
		fila.setColeccion(coleccion);
		fila.setProducto(producto);
		fila.setPosicion(req.posicion() == null ? 0 : req.posicion());
		asignaciones.save(fila);
		auditoria.registrar("coleccion_productos", fila.getId(), "coleccion.asignar");
	}

	@Transactional
	public void quitar(Long tiendaId, Long coleccionId, Long productoId) {
		ColeccionProducto fila = asignaciones.findByColeccionIdAndProductoId(coleccionId, productoId)
				.orElseThrow(() -> new NoEncontradoException("asignación"));
		if (!fila.getProducto().getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("asignación");
		}
		Long filaId = fila.getId();
		asignaciones.delete(fila);
		auditoria.registrar("coleccion_productos", filaId, "coleccion.quitar");
	}
}
