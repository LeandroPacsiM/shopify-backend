package com.tienda.plataforma.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.pagos.entity.TiendaPasarela;

public interface TiendaPasarelaRepository extends JpaRepository<TiendaPasarela, Long> {

	Optional<TiendaPasarela> findByTiendaIdAndPasarelaId(Long tiendaId, Long pasarelaId);

	List<TiendaPasarela> findByTiendaIdAndActivoTrue(Long tiendaId);
}
