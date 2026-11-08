package com.tienda.plataforma.tenants.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.tenants.entity.Direccion;

public interface DireccionRepository extends JpaRepository<Direccion, Long> {

	List<Direccion> findByClienteId(Long clienteId);
}
