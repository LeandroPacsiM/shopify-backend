package com.tienda.plataforma.tenants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.tienda.plataforma.tenants.service.TiendaPropiedadService;

/**
 * Propiedad única (ADR-016, plan v5): transferencia atómica, confirmación
 * SUPER_ADMIN y concurrencia real sobre owners (condición 2 de ejecución).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PropiedadTiendaTest {

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
	@Autowired
	TiendaPropiedadService propiedad;

	Tienda tienda;
	Usuario owner;
	Usuario admin;
	Usuario operador;
	Usuario externo;
	Usuario superAdmin;
	String jwtOwner;
	String jwtAdmin;
	String jwtSuper;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("OW-" + tag);
		tienda.setSlug("ow-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		owner = vincular("owo-" + tag, TiendaUsuario.STORE_OWNER);
		admin = vincular("owa-" + tag, TiendaUsuario.ADMIN);
		operador = vincular("owo2-" + tag, TiendaUsuario.OPERADOR);
		externo = nuevoUsuario("owe-" + tag);
		superAdmin = nuevoUsuario("ows-" + tag);
		jwtOwner = jwtService.acceso(owner.getId(), List.of("admin"));
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));
		// El claim manda en el JWT (sin fila en usuario_roles).
		jwtSuper = jwtService.acceso(superAdmin.getId(), List.of("super_admin"));
	}

	@Test
	void transferirIntercambiaOwnerAtomico() throws Exception {
		mvc.perform(patch("/api/admin/tienda/propietario")
				.header("Authorization", "Bearer " + jwtOwner)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nuevoOwnerId\":" + admin.getId() + "}"))
				.andExpect(status().isNoContent());

		assertEquals(TiendaUsuario.ADMIN, rolDe(owner));
		assertEquals(TiendaUsuario.STORE_OWNER, rolDe(admin));
		assertEquals(1, ownersDe());
	}

	@Test
	void soloOwnerTransfiere() throws Exception {
		mvc.perform(patch("/api/admin/tienda/propietario")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nuevoOwnerId\":" + operador.getId() + "}"))
				.andExpect(status().isForbidden());

		assertThrows(com.tienda.plataforma.shared.exception.SolicitudInvalidaException.class,
				() -> propiedad.transferir(tienda.getId(), owner.getId(), externo.getId()));
		assertThrows(com.tienda.plataforma.shared.exception.SolicitudInvalidaException.class,
				() -> propiedad.transferir(tienda.getId(), owner.getId(), owner.getId()));
		assertEquals(1, ownersDe());
	}

	@Test
	void ownerUnicoNoSeDegradaNiSeRevocaNiSeCreaDirecto() throws Exception {
		String base = "/api/admin/usuarios";
		mvc.perform(patch(base + "/{id}/rol", owner.getId())
				.header("Authorization", "Bearer " + jwtOwner)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rol\":\"admin\"}"))
				.andExpect(status().isConflict());
		mvc.perform(delete(base + "/{id}", owner.getId())
				.header("Authorization", "Bearer " + jwtOwner)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isConflict());
		mvc.perform(post(base)
				.header("Authorization", "Bearer " + jwtOwner)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + externo.getEmail() + "\",\"rol\":\"store_owner\"}"))
				.andExpect(status().isConflict());
		assertEquals(1, ownersDe());
	}

	@Test
	void superAdminConfirmaOwnerDeTiendaSinOwner() throws Exception {
		// Tienda existente sin owner (estado F1/F2): solo SUPER_ADMIN confirma.
		Tienda vieja = new Tienda();
		String tag = UUID.randomUUID().toString().substring(0, 8);
		vieja.setNombre("OLD-" + tag);
		vieja.setSlug("old-" + tag);
		vieja.setActiva(true);
		tiendas.save(vieja);
		Usuario candidato = nuevoUsuario("oldc-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(vieja);
		v.setUsuario(candidato);
		v.setRolTienda(TiendaUsuario.ADMIN);
		vinculos.save(v);

		mvc.perform(patch("/api/ops/tiendas/{id}/propietario", vieja.getId())
				.header("Authorization", "Bearer " + jwtSuper)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuarioId\":" + candidato.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(vieja.getSlug()));

		assertEquals(TiendaUsuario.STORE_OWNER,
				vinculos.findByTiendaIdAndUsuarioId(vieja.getId(), candidato.getId())
						.orElseThrow().getRolTienda());

		// Con owner ya confirmado: 409 (usar transferencia).
		mvc.perform(patch("/api/ops/tiendas/{id}/propietario", vieja.getId())
				.header("Authorization", "Bearer " + jwtSuper)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuarioId\":" + candidato.getId() + "}"))
				.andExpect(status().isConflict());

		// No super_admin: falso 404.
		mvc.perform(patch("/api/ops/tiendas/{id}/propietario", vieja.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"usuarioId\":" + candidato.getId() + "}"))
				.andExpect(status().isNotFound());
	}

	private long ownersDe() {
		return vinculos.findByTiendaId(tienda.getId()).stream()
				.filter(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda()))
				.count();
	}

	private String rolDe(Usuario u) {
		return vinculos.findByTiendaIdAndUsuarioId(tienda.getId(), u.getId())
				.orElseThrow().getRolTienda();
	}

	private Usuario vincular(String tag, String rol) {
		Usuario u = nuevoUsuario(tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(u);
		v.setRolTienda(rol);
		vinculos.save(v);
		return u;
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
