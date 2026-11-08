package com.tienda.plataforma.envios.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.envios.entity.EnvioItem;

public interface EnvioItemRepository extends JpaRepository<EnvioItem, Long> {

	List<EnvioItem> findByEnvioId(Long envioId);

	List<EnvioItem> findByPedidoItemId(Long pedidoItemId);
}
