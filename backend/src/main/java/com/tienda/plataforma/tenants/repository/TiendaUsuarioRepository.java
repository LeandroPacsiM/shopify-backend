package com.tienda.plataforma.tenants.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.tenants.entity.TiendaUsuario;

public interface TiendaUsuarioRepository extends JpaRepository<TiendaUsuario, Long> {

	Optional<TiendaUsuario> findByTiendaIdAndUsuarioId(Long tiendaId, Long usuarioId);

	List<TiendaUsuario> findByTiendaId(Long tiendaId);

	List<TiendaUsuario> findByUsuarioId(Long usuarioId);
}
