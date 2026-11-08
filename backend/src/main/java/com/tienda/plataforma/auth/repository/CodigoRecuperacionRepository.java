package com.tienda.plataforma.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.CodigoRecuperacion;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	List<CodigoRecuperacion> findByUsuarioId(Long usuarioId);
}
