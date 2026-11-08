package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.PedidoImpuesto;

public interface PedidoImpuestoRepository extends JpaRepository<PedidoImpuesto, Long> {

	List<PedidoImpuesto> findByPedidoId(Long pedidoId);
}
