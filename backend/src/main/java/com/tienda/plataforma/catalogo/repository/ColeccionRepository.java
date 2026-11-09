package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.Coleccion;

public interface ColeccionRepository extends JpaRepository<Coleccion, Long> {

	Optional<Coleccion> findBySlug(String slug);

	List<Coleccion> findByActivoTrueOrderByIdAsc();
}
