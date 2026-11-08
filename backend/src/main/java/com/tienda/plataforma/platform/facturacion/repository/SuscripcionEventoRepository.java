package com.tienda.plataforma.platform.facturacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.platform.facturacion.entity.SuscripcionEvento;

public interface SuscripcionEventoRepository extends JpaRepository<SuscripcionEvento, Long> {

	boolean existsByReferenciaExterna(String referenciaExterna);
}
