package com.tienda.plataforma.envios.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.envios.entity.Transportista;

public interface TransportistaRepository extends JpaRepository<Transportista, Long> {

	Optional<Transportista> findByCodigo(String codigo);

	List<Transportista> findByActivoTrue();
}
