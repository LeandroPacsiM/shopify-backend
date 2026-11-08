package com.tienda.plataforma.inventario.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.inventario.entity.ArticuloInventario;

public interface ArticuloInventarioRepository extends JpaRepository<ArticuloInventario, Long> {

	Optional<ArticuloInventario> findByVarianteId(Long varianteId);
}
