package com.tienda.plataforma.tenants;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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

/** RF-ADMIN-04: equipo solo para admin; la tienda conserva un admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EquipoTiendaTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository vinculos;

	Tienda tienda;
	Usuario admin;
	Usuario futuro;
	String jwtAdmin;
	String jwtOperador;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("E-" + tag);
		tienda.setSlug("e-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		admin = nuevoUsuario("ea-" + tag);
		vincular(admin, TiendaUsuario.ADMIN);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		Usuario operador = nuevoUsuario("eo-" + tag);
		vincular(operador, TiendaUsuario.OPERADOR);
		jwtOperador = jwtService.acceso(operador.getId(), List.of("operador"));

		futuro = nuevoUsuario("ef-" + tag);
	}

	@Test
	void adminAgregaCambiaYRevoca() throws Exception {
		String base = "/api/admin/usuarios";
		mvc.perform(post(base)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + futuro.getEmail() + "\",\"rol\":\"operador\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("operador"));
		mvc.perform(post(base)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + futuro.getEmail() + "\",\"rol\":\"operador\"}"))
				.andExpect(status().isConflict());

		mvc.perform(patch(base + "/{id}/rol", futuro.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"admin\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("admin"));

		mvc.perform(get(base)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3));

		mvc.perform(delete(base + "/{id}", futuro.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());
		mvc.perform(delete(base + "/{id}", futuro.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNotFound());
	}

	@Test
	void ultimoAdminNoSeDegradaNiSeRevoca() throws Exception {
		String base = "/api/admin/usuarios";
		mvc.perform(patch(base + "/{id}/rol", admin.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"operador\"}"))
				.andExpect(status().isConflict());
		mvc.perform(delete(base + "/{id}", admin.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isConflict());
	}

	@Test
	void operadorNoEntra() throws Exception {
		mvc.perform(get("/api/admin/usuarios")
				.header("Authorization", "Bearer " + jwtOperador)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/usuarios")
				.header("Authorization", "Bearer " + jwtOperador)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + futuro.getEmail() + "\",\"rol\":\"operador\"}"))
				.andExpect(status().isForbidden());
	}

	private void vincular(Usuario usuario, String rol) {
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(usuario);
		v.setRolTienda(rol);
		vinculos.save(v);
	}

	private Usuario nuevoUsuario(String tag) {
		Usuario u = new Usuario();
		u.setEmail(tag + "@example.com");
		u.setPasswordHash("x");
		u.setActivo(true);
		u.setEmailVerificado(true);
		u.setIntentosFallidos(0);
		return usuarios.save(u);
	}
}
