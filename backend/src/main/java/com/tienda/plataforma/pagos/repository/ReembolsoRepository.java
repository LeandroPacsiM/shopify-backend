package com.tienda.plataforma.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tienda.plataforma.pagos.entity.Reembolso;

import jakarta.persistence.LockModeType;

public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {

	List<Reembolso> findByPagoId(Long pagoId);

	List<Reembolso> findByPedidoId(Long pedidoId);

	Optional<Reembolso> findByTransaccionExternaId(String transaccionExternaId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reembolso r where r.id = :id")
	Optional<Reembolso> findConBloqueoById(@Param("id") Long id);
}
