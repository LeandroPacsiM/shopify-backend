package com.tienda.plataforma.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.tienda.plataforma.auth.entity.CodigoCliente;

import jakarta.persistence.LockModeType;

public interface CodigoClienteRepository extends JpaRepository<CodigoCliente, Long> {

	List<CodigoCliente> findByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
			Long tiendaId, String email);

	/** Verifica bajo bloqueo: intentos + consumo + perfil en una TX. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CodigoCliente> findTopByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
			Long tiendaId, String email);

	long countByTiendaIdAndEmailAndCreadoEnAfter(Long tiendaId, String email, Instant desde);

	void deleteByExpiraEnBefore(Instant limite);
}
