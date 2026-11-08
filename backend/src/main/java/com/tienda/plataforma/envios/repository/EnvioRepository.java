package com.tienda.plataforma.envios.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.envios.entity.Envio;

public interface EnvioRepository extends JpaRepository<Envio, Long> {

	List<Envio> findByPedidoId(Long pedidoId);

	Optional<Envio> findByNumeroTracking(String numeroTracking);
}
