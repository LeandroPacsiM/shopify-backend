package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.DescuentoColeccion;

public interface DescuentoColeccionRepository extends JpaRepository<DescuentoColeccion, Long> {

	List<DescuentoColeccion> findByDescuentoId(Long descuentoId);
}
