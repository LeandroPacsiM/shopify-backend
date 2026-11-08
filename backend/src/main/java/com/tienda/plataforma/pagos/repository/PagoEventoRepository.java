package com.tienda.plataforma.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.pagos.entity.PagoEvento;

public interface PagoEventoRepository extends JpaRepository<PagoEvento, Long> {

	List<PagoEvento> findByPagoId(Long pagoId);

	Optional<PagoEvento> findByPagoIdAndReferenciaExterna(Long pagoId, String referenciaExterna);
}
