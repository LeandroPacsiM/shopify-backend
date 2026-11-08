package com.tienda.plataforma.inventario.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.inventario.entity.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

	List<MovimientoInventario> findByNivelId(Long nivelId);

	List<MovimientoInventario> findByReferencia(String referencia);
}
