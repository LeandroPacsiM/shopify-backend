package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.MetodoEnvio;

public interface MetodoEnvioRepository extends JpaRepository<MetodoEnvio, Long> {

	List<MetodoEnvio> findByTiendaIdAndActivoTrue(Long tiendaId);
}
