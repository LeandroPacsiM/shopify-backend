package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.PedidoDireccion;

public interface PedidoDireccionRepository extends JpaRepository<PedidoDireccion, Long> {

	List<PedidoDireccion> findByPedidoId(Long pedidoId);
}
