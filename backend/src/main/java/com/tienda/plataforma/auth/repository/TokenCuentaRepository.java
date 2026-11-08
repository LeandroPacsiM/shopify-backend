package com.tienda.plataforma.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.auth.entity.TokenCuenta;

public interface TokenCuentaRepository extends JpaRepository<TokenCuenta, Long> {

	Optional<TokenCuenta> findByTokenHash(String tokenHash);

	List<TokenCuenta> findByUsuarioId(Long usuarioId);

	List<TokenCuenta> findByUsuarioIdAndTipoAndUsadoEnIsNull(Long usuarioId, String tipo);
}
