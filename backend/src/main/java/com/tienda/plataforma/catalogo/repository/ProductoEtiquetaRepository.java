package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.ProductoEtiqueta;

public interface ProductoEtiquetaRepository extends JpaRepository<ProductoEtiqueta, Long> {

	List<ProductoEtiqueta> findByProductoId(Long productoId);

	List<ProductoEtiqueta> findByEtiquetaId(Long etiquetaId);

	Optional<ProductoEtiqueta> findByProductoIdAndEtiquetaId(Long productoId, Long etiquetaId);
}
