package com.tienda.plataforma.catalogo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tienda.plataforma.catalogo.entity.ProductoVariante;

import jakarta.persistence.LockModeType;

public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {

	Optional<ProductoVariante> findBySku(String sku);

	List<ProductoVariante> findByProductoId(Long productoId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ProductoVariante> findConBloqueoById(Long id);
}
