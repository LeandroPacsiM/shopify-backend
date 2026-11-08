package com.tienda.plataforma.auth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.RegistroAuditoria;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

	List<RegistroAuditoria> findByEntidadAndEntidadId(String entidad, Long entidadId);

	List<RegistroAuditoria> findByUsuarioId(Long usuarioId);
}
