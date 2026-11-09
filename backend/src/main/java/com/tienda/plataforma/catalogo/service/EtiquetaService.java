package com.tienda.plataforma.catalogo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.dto.AsignarEtiquetaRequest;
import com.tienda.plataforma.catalogo.dto.CrearEtiquetaRequest;
import com.tienda.plataforma.catalogo.dto.EtiquetaResponse;
import com.tienda.plataforma.catalogo.entity.Etiqueta;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoEtiqueta;
import com.tienda.plataforma.catalogo.repository.EtiquetaRepository;
import com.tienda.plataforma.catalogo.repository.ProductoEtiquetaRepository;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;

/**
 * RF-ADMIN-01: etiquetas reutilizables (globales) aplicadas a productos
 * propios. Lo público filtra por tienda.
 */
@Service
public class EtiquetaService {

	private final EtiquetaRepository etiquetas;
	private final ProductoEtiquetaRepository asignaciones;
	private final ProductoRepository productos;
	private final AuditoriaService auditoria;

	public EtiquetaService(EtiquetaRepository etiquetas,
			ProductoEtiquetaRepository asignaciones, ProductoRepository productos,
			AuditoriaService auditoria) {
		this.etiquetas = etiquetas;
		this.asignaciones = asignaciones;
		this.productos = productos;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<EtiquetaResponse> listar() {
		return etiquetas.findAllByOrderByIdAsc().stream().map(EtiquetaResponse::de).toList();
	}

	@Transactional
	public EtiquetaResponse crear(CrearEtiquetaRequest req) {
		if (etiquetas.findBySlug(req.slug().trim()).isPresent()
				|| etiquetas.findByNombre(req.nombre().trim()).isPresent()) {
			throw new ConflictoException("etiqueta en uso");
		}
		Etiqueta etiqueta = new Etiqueta();
		etiqueta.setNombre(req.nombre().trim());
		etiqueta.setSlug(req.slug().trim());
		etiquetas.save(etiqueta);
		auditoria.registrar("etiquetas", etiqueta.getId(), "etiqueta.crear");
		return EtiquetaResponse.de(etiqueta);
	}

	@Transactional
	public void asignar(Long tiendaId, Long etiquetaId, AsignarEtiquetaRequest req) {
		Etiqueta etiqueta = etiquetas.findById(etiquetaId)
				.orElseThrow(() -> new NoEncontradoException("etiqueta"));
		Producto producto = productos.findById(req.productoId())
				.orElseThrow(() -> new NoEncontradoException("producto"));
		if (!producto.getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("producto");
		}
		if (asignaciones.findByProductoIdAndEtiquetaId(producto.getId(), etiqueta.getId()).isPresent()) {
			throw new ConflictoException("etiqueta ya asignada");
		}
		ProductoEtiqueta fila = new ProductoEtiqueta();
		fila.setProducto(producto);
		fila.setEtiqueta(etiqueta);
		asignaciones.save(fila);
		auditoria.registrar("producto_etiquetas", fila.getId(), "etiqueta.asignar");
	}

	@Transactional
	public void quitar(Long tiendaId, Long etiquetaId, Long productoId) {
		ProductoEtiqueta fila = asignaciones.findByProductoIdAndEtiquetaId(productoId, etiquetaId)
				.orElseThrow(() -> new NoEncontradoException("asignación"));
		if (!fila.getProducto().getTienda().getId().equals(tiendaId)) {
			throw new NoEncontradoException("asignación");
		}
		Long filaId = fila.getId();
		asignaciones.delete(fila);
		auditoria.registrar("producto_etiquetas", filaId, "etiqueta.quitar");
	}
}
