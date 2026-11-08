package com.tienda.plataforma.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.Sesion;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

	Optional<Sesion> findByTokenHash(String tokenHash);

	List<Sesion> findByFamiliaId(UUID familiaId);

	List<Sesion> findByUsuarioId(Long usuarioId);

	long deleteByExpiraEnBefore(Instant limite);
}
