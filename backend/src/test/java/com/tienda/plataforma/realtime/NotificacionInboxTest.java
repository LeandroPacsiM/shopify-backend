package com.tienda.plataforma.realtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** V16: lo avisado por WS queda en bandeja, aislado por tienda. Rollback al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificacionInboxTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	NotificacionService notificaciones;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;

	Tienda tiendaA;
	Tienda tiendaB;
	String jwtA;
	String jwtB;
	Long avisoA;

	@BeforeEach
	void fixtures() {
		tiendaA = nuevaTienda("nbx-a-");
		tiendaB = nuevaTienda("nbx-b-");
		jwtA = jwtAdmin(tiendaA);
		jwtB = jwtAdmin(tiendaB);

		avisoA = notificaciones.notificar(tiendaA.getId(), NotificacionService.NUEVO_PEDIDO,
				Map.of("pedidoId", 1, "numero", "NBX-1")).id();
		notificaciones.notificar(tiendaA.getId(), NotificacionService.PEDIDO_PAGADO,
				Map.of("pedidoId", 2, "numero", "NBX-2"));
		notificaciones.notificar(tiendaB.getId(), NotificacionService.NUEVO_PEDIDO,
				Map.of("pedidoId", 3, "numero", "NBX-3"));
	}

	@Test
	void bandejaAisladaPorTienda() throws Exception {
		mvc.perform(get("/api/admin/notificaciones")
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaA.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content.length()").value(2));

		mvc.perform(get("/api/admin/notificaciones")
				.header("Authorization", "Bearer " + jwtB)
				.header("X-Tienda-Slug", tiendaB.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mvc.perform(get("/api/admin/notificaciones")
				.param("page", "1").param("size", "1")
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaA.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(1));
	}

	@Test
	void leerDescuentaBadge() throws Exception {
		mvc.perform(get("/api/admin/notificaciones/no-leidas")
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaA.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.noLeidas").value(2));

		mvc.perform(patch("/api/admin/notificaciones/{id}/leer", avisoA)
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaA.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(avisoA));

		mvc.perform(get("/api/admin/notificaciones/no-leidas")
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaA.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.noLeidas").value(1));
	}

	@Test
	void avisoAjenoNoExiste() throws Exception {
		mvc.perform(patch("/api/admin/notificaciones/{id}/leer", avisoA)
				.header("Authorization", "Bearer " + jwtB)
				.header("X-Tienda-Slug", tiendaB.getSlug()))
				.andExpect(status().isNotFound());

		mvc.perform(get("/api/admin/notificaciones")
				.header("Authorization", "Bearer " + jwtA)
				.header("X-Tienda-Slug", tiendaB.getSlug()))
				.andExpect(status().isForbidden());
	}

	private Tienda nuevaTienda(String prefijo) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre(prefijo + tag);
		tienda.setSlug(prefijo + tag);
		tienda.setActiva(true);
		return tiendas.save(tienda);
	}

	private String jwtAdmin(Tienda tienda) {
		Usuario admin = new Usuario();
		admin.setEmail("nb-" + UUID.randomUUID() + "@example.com");
		admin.setPasswordHash("x");
		admin.setActivo(true);
		admin.setEmailVerificado(true);
		admin.setIntentosFallidos(0);
		usuarios.save(admin);
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(admin);
		vinculo.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(vinculo);
		return jwtService.acceso(admin.getId(), List.of("admin"));
	}
}
