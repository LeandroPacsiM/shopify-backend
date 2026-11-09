package com.tienda.plataforma.realtime;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.realtime.entity.Notificacion;
import com.tienda.plataforma.realtime.repository.NotificacionRepository;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** Purga: solo leídas hace +90 días; no leídas y recientes quedan. Rollback al final. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificacionPurgaTest {

	@Autowired
	NotificacionService notificaciones;
	@Autowired
	NotificacionRepository repositorio;
	@Autowired
	TiendaRepository tiendas;

	@Test
	void purgaSoloLeidaVieja() {
		Tienda tienda = new Tienda();
		tienda.setNombre("NP-" + UUID.randomUUID().toString().substring(0, 8));
		tienda.setSlug("np-" + UUID.randomUUID().toString().substring(0, 8));
		tienda.setActiva(true);
		tiendas.save(tienda);

		Notificacion vieja = aviso(tienda, Instant.now().minusSeconds(91 * 24 * 60 * 60));
		Notificacion reciente = aviso(tienda, Instant.now().minusSeconds(10 * 24 * 60 * 60));
		Notificacion sinLeer = new Notificacion();
		sinLeer.setTienda(tienda);
		sinLeer.setTipo(NotificacionService.NUEVO_PEDIDO);
		sinLeer.setDatos(Map.of("numero", "NP-3"));
		repositorio.save(sinLeer);

		notificaciones.purgarLeidas();

		assertTrue(repositorio.findById(vieja.getId()).isEmpty());
		assertTrue(repositorio.findById(reciente.getId()).isPresent());
		assertTrue(repositorio.findById(sinLeer.getId()).isPresent());
	}

	private Notificacion aviso(Tienda tienda, Instant leida) {
		Notificacion n = new Notificacion();
		n.setTienda(tienda);
		n.setTipo(NotificacionService.NUEVO_PEDIDO);
		n.setDatos(Map.of("numero", "NP"));
		n.setLeidaEn(leida);
		return repositorio.save(n);
	}
}
