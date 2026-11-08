package com.tienda.plataforma.platform.facturacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;

public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

	Optional<Suscripcion> findByTiendaId(Long tiendaId);

	Optional<Suscripcion> findByStripeSubscriptionId(String stripeSubscriptionId);
}
