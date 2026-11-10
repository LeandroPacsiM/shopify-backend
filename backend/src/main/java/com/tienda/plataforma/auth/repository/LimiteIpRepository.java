package com.tienda.plataforma.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.LimiteIp;
import com.tienda.plataforma.auth.entity.LimiteIpId;

/**
 * Contadores por IP. El incremento es un upsert nativo atómico en
 * `ClienteAuthService` (PostgreSQL no compara `inet = varchar` en consultas
 * derivadas); este repositorio solo existe para el mapeo/validación.
 */
public interface LimiteIpRepository extends JpaRepository<LimiteIp, LimiteIpId> {
}
