package com.tienda.plataforma.inventario.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.inventario.entity.Ubicacion;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Long> {

	List<Ubicacion> findByTiendaId(Long tiendaId);
}
