package com.tienda.plataforma.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tienda.plataforma.auth.entity.UsuarioRol;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, Long> {

	@Query("select ur.rol.nombre from UsuarioRol ur where ur.usuario.id = :usuarioId")
	List<String> nombresDeRoles(Long usuarioId);
}
