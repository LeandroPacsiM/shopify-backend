package com.tienda.plataforma.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.pagos.entity.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	List<Pago> findByPedidoId(Long pedidoId);

	Optional<Pago> findByPasarelaIdAndTransaccionExternaId(Long pasarelaId, String transaccionExternaId);
}
