package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.DescuentoProducto;

public interface DescuentoProductoRepository extends JpaRepository<DescuentoProducto, Long> {

	List<DescuentoProducto> findByDescuentoId(Long descuentoId);
}
