package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.TasaImpuesto;

public interface TasaImpuestoRepository extends JpaRepository<TasaImpuesto, Long> {

	List<TasaImpuesto> findByTiendaIdAndActivoTrue(Long tiendaId);
}
