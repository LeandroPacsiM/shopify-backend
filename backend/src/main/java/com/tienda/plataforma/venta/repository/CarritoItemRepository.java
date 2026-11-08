package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.CarritoItem;

public interface CarritoItemRepository extends JpaRepository<CarritoItem, Long> {

	List<CarritoItem> findByCarritoId(Long carritoId);

	Optional<CarritoItem> findByCarritoIdAndVarianteId(Long carritoId, Long varianteId);
}
