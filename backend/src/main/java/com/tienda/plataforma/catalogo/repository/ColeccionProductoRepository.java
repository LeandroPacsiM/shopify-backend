package com.tienda.plataforma.catalogo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.ColeccionProducto;

public interface ColeccionProductoRepository extends JpaRepository<ColeccionProducto, Long> {

	List<ColeccionProducto> findByProductoId(Long productoId);
}
