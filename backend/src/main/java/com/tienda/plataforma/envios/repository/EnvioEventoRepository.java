package com.tienda.plataforma.envios.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.envios.entity.EnvioEvento;

public interface EnvioEventoRepository extends JpaRepository<EnvioEvento, Long> {

	List<EnvioEvento> findByEnvioIdOrderByOcurridoEnAsc(Long envioId);

	boolean existsByEnvioIdAndReferenciaExterna(Long envioId, String referenciaExterna);
}
