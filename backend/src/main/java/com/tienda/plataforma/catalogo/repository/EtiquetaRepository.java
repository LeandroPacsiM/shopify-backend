package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.Etiqueta;

public interface EtiquetaRepository extends JpaRepository<Etiqueta, Long> {

	Optional<Etiqueta> findBySlug(String slug);

	Optional<Etiqueta> findByNombre(String nombre);

	List<Etiqueta> findAllByOrderByIdAsc();
}
