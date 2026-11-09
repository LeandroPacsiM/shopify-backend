package com.tienda.plataforma.venta.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.CheckoutDescuento;

public interface CheckoutDescuentoRepository extends JpaRepository<CheckoutDescuento, Long> {

	List<CheckoutDescuento> findByCheckoutSesionId(Long checkoutSesionId);
}
