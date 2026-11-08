package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;

public interface HistorialEstadoPedidoRepository extends JpaRepository<HistorialEstadoPedido, Long> {

	List<HistorialEstadoPedido> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
