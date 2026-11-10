package com.tienda.plataforma.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.ClienteSesion;

public interface ClienteSesionRepository extends JpaRepository<ClienteSesion, Long> {

	Optional<ClienteSesion> findByTokenHash(String tokenHash);

	List<ClienteSesion> findByFamiliaId(java.util.UUID familiaId);

	void deleteByExpiraEnBefore(Instant limite);
}
