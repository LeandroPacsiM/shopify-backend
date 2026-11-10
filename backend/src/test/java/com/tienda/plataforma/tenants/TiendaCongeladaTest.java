package com.tienda.plataforma.tenants;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** RF-BILL: tienda congelada bloquea todo salvo su facturación. Rollback al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TiendaCongeladaTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	SuscripcionRepository suscripciones;

	Tienda tienda;
	String jwtAdmin;
	String jwtCliente;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("FZ-" + tag);
		tienda.setSlug("fz-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario admin = nuevoUsuario("fza-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(admin);
		v.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(v);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		Usuario comprador = nuevoUsuario("fzc-" + tag);
		Cliente c = new Cliente();
		c.setTienda(tienda);
		c.setAceptaMarketing(false);
		c.setEmail(comprador.getEmail());
		clientes.save(c);
		jwtCliente = jwtService.accesoCliente(c.getId(), tienda.getId());

		Suscripcion s = new Suscripcion();
		s.setTienda(tienda);
		s.setPlan(Suscripcion.MENSUAL);
		s.setEstado(Suscripcion.PENDIENTE);
		suscripciones.save(s);
	}

	@Test
	void congeladaBloqueaSalvoFacturacion() throws Exception {
		tienda.setActiva(false);
		tiendas.save(tienda);

		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug())
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/pedidos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/facturacion")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk());

		tienda.setActiva(true);
		tiendas.save(tienda);

		mvc.perform(get("/api/admin/pedidos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk());
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
