package com.tienda.plataforma.tenants.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.tenants.entity.Tienda;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {

	Optional<Tienda> findBySlug(String slug);
}
