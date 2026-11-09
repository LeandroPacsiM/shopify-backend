package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.ColeccionProducto;

public interface ColeccionProductoRepository extends JpaRepository<ColeccionProducto, Long> {

	List<ColeccionProducto> findByProductoId(Long productoId);

	List<ColeccionProducto> findByColeccionIdOrderByPosicionAsc(Long coleccionId);

	Optional<ColeccionProducto> findByColeccionIdAndProductoId(Long coleccionId, Long productoId);
}
