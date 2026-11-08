package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.Carrito;

public interface CarritoRepository extends JpaRepository<Carrito, Long> {

	Optional<Carrito> findByToken(String token);

	List<Carrito> findByClienteId(Long clienteId);
}
