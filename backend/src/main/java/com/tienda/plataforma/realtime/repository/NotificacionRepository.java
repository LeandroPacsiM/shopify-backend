package com.tienda.plataforma.realtime.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.realtime.entity.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	List<Notificacion> findByTiendaIdOrderByCreadoEnDesc(Long tiendaId);

	Page<Notificacion> findByTiendaId(Long tiendaId, Pageable paginacion);

	long countByTiendaIdAndLeidaEnIsNull(Long tiendaId);

	long deleteByLeidaEnBefore(Instant limite);
}
