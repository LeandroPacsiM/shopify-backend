package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.catalogo.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

	Optional<Producto> findBySlug(String slug);

	List<Producto> findByTiendaIdAndEstado(Long tiendaId, String estado);
}
