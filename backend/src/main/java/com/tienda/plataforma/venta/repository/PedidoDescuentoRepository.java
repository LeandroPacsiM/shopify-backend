package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.PedidoDescuento;

public interface PedidoDescuentoRepository extends JpaRepository<PedidoDescuento, Long> {

	List<PedidoDescuento> findByPedidoId(Long pedidoId);
}
