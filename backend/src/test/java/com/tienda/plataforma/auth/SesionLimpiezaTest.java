package com.tienda.plataforma.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Sesion;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.SesionRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;

/** RF-AUTH-05: el job purga lo vencido hace más de la gracia; lo demás queda. Rollback al final. */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class SesionLimpiezaTest {

	@Autowired
	AuthService authService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	SesionRepository sesiones;

	@Test
	void purgaSoloVencidaConGracia() {
		Usuario u = new Usuario();
		u.setEmail("sl-" + UUID.randomUUID() + "@example.com");
		u.setPasswordHash("x");
		u.setActivo(true);
		u.setEmailVerificado(true);
		u.setIntentosFallidos(0);
		usuarios.save(u);

		Sesion vieja = sesion(u, Instant.now().minusSeconds(8 * 24 * 60 * 60));
		Sesion reciente = sesion(u, Instant.now().minusSeconds(24 * 60 * 60));
		Sesion vigente = sesion(u, Instant.now().plusSeconds(30 * 24 * 60 * 60));

		authService.limpiarSesionesExpiradas();

		assertTrue(sesiones.findById(vieja.getId()).isEmpty());
		assertTrue(sesiones.findById(reciente.getId()).isPresent());
		assertTrue(sesiones.findById(vigente.getId()).isPresent());
	}

	private Sesion sesion(Usuario u, Instant expira) {
		Sesion s = new Sesion();
		s.setUsuario(u);
		s.setTokenHash("limpieza-" + UUID.randomUUID());
		s.setFamiliaId(UUID.randomUUID());
		s.setExpiraEn(expira);
		return sesiones.save(s);
	}
}
