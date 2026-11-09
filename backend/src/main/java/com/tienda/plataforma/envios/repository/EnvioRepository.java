package com.tienda.plataforma.envios.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tienda.plataforma.envios.entity.Envio;

import jakarta.persistence.LockModeType;

public interface EnvioRepository extends JpaRepository<Envio, Long> {

	List<Envio> findByPedidoId(Long pedidoId);

	Optional<Envio> findByNumeroTracking(String numeroTracking);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Envio e where e.numeroTracking = :numeroTracking")
	Optional<Envio> findConBloqueoByNumeroTracking(@Param("numeroTracking") String numeroTracking);
}
