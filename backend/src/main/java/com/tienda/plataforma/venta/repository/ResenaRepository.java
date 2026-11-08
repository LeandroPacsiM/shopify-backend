package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.Resena;

public interface ResenaRepository extends JpaRepository<Resena, Long> {

	List<Resena> findByProductoIdAndAprobadaTrue(Long productoId);

	List<Resena> findByClienteId(Long clienteId);

	Optional<Resena> findByProductoIdAndClienteId(Long productoId, Long clienteId);
}
