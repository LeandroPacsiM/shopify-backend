package com.tienda.plataforma.tenants.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.tenants.entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

	Optional<Cliente> findByUsuarioId(Long usuarioId);

	List<Cliente> findByTiendaId(Long tiendaId);
}
