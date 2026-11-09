package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tienda.plataforma.venta.entity.CheckoutSesion;

import jakarta.persistence.LockModeType;

public interface CheckoutSesionRepository extends JpaRepository<CheckoutSesion, Long> {

	List<CheckoutSesion> findByCarritoId(Long carritoId);

	List<CheckoutSesion> findByClienteId(Long clienteId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CheckoutSesion> findConBloqueoById(Long id);
}
