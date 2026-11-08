package com.tienda.plataforma.realtime.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tienda.plataforma.realtime.entity.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	List<Notificacion> findByTiendaIdOrderByCreadoEnDesc(Long tiendaId);

	long countByTiendaIdAndLeidaEnIsNull(Long tiendaId);

	long deleteByLeidaEnBefore(Instant limite);
}
