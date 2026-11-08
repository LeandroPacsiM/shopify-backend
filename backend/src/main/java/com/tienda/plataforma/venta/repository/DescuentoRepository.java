package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tienda.plataforma.venta.entity.Descuento;

import jakarta.persistence.LockModeType;

public interface DescuentoRepository extends JpaRepository<Descuento, Long> {

	Optional<Descuento> findByCodigoAndTiendaId(String codigo, Long tiendaId);

	List<Descuento> findByTiendaIdAndActivoTrue(Long tiendaId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Descuento> findConBloqueoById(Long id);
}
