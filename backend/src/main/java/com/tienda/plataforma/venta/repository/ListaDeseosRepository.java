package com.tienda.plataforma.venta.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.venta.entity.ListaDeseos;

public interface ListaDeseosRepository extends JpaRepository<ListaDeseos, Long> {

	List<ListaDeseos> findByClienteId(Long clienteId);

	Optional<ListaDeseos> findByClienteIdAndNombre(Long clienteId, String nombre);
}
