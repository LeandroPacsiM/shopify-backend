package com.tienda.plataforma.venta;

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
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.venta.entity.Resena;
import com.tienda.plataforma.venta.repository.ResenaRepository;

/** FEAT-01/RF-REV: wishlist legible y borrable, reseñas públicas moderadas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CuentaWishlistResenasTest {

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
	ProductoRepository productos;
	@Autowired
	ResenaRepository resenas;

	Tienda tienda;
	Producto producto;
	Cliente cliente;
	String jwtCliente;
	String jwtAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("W-" + tag);
		tienda.setSlug("w-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario dueno = nuevoUsuario("wc-" + tag);
		cliente = new Cliente();
		cliente.setUsuario(dueno);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);
		jwtCliente = jwtService.accesoCliente(cliente.getId(), tienda.getId());

		Usuario admin = nuevoUsuario("wa-" + tag);
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(admin);
		vinculo.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(vinculo);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("Deseado " + tag);
		producto.setSlug("wsp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
	}

	@Test
	void wishlistAgregaSinDuplicarListaYQuita() throws Exception {
		String base = "/api/storefront/" + tienda.getSlug() + "/cuenta/wishlist";
		for (int i = 0; i < 2; i++) {
			mvc.perform(post(base + "/{id}", producto.getId())
					.header("Authorization", "Bearer " + jwtCliente)
					.header("X-Tienda-Slug", tienda.getSlug()))
					.andExpect(status().isOk());
		}
		mvc.perform(get(base)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].slug").value(producto.getSlug()));

		mvc.perform(delete(base + "/{id}", producto.getId())
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());
		mvc.perform(get(base)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(delete(base + "/{id}", producto.getId())
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNotFound());
	}

	@Test
	void wishlistDeOtraTiendaNoExiste() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda otra = new Tienda();
		otra.setNombre("WO-" + tag);
		otra.setSlug("wo-" + tag);
		otra.setActiva(true);
		tiendas.save(otra);
		Producto ajeno = new Producto();
		ajeno.setTienda(otra);
		ajeno.setTitulo("Ajeno " + tag);
		ajeno.setSlug("wso-" + tag);
		ajeno.setEstado(Producto.ACTIVO);
		productos.save(ajeno);

		mvc.perform(post("/api/storefront/" + tienda.getSlug() + "/cuenta/wishlist/{id}", ajeno.getId())
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNotFound());
	}

	@Test
	void resenaSeModeraAntesDePublicarse() throws Exception {
		String cuenta = "/api/storefront/" + tienda.getSlug() + "/cuenta/resenas";
		String publicas = "/api/storefront/" + tienda.getSlug() + "/productos/" + producto.getSlug()
				+ "/resenas";
		String cuerpo = "{\"productoId\":" + producto.getId()
				+ ",\"calificacion\":5,\"titulo\":\"Buena\",\"comentario\":\"Me sirvió\"}";

		mvc.perform(post(cuenta)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo))
				.andExpect(status().isOk());
		mvc.perform(post(cuenta)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo))
				.andExpect(status().isConflict());
		mvc.perform(get(cuenta)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].aprobada").value(false));
		mvc.perform(get(publicas)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		Long id = resenas.findByProductoIdAndClienteId(producto.getId(), cliente.getId())
				.orElseThrow().getId();
		mvc.perform(patch("/api/admin/resenas/{id}/aprobar", id)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aprobada").value(true));

		mvc.perform(get(publicas)).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].titulo").value("Buena"));
	}

	@Test
	void adminNoModeraResenaDeOtraTienda() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda otra = new Tienda();
		otra.setNombre("WR-" + tag);
		otra.setSlug("wr-" + tag);
		otra.setActiva(true);
		tiendas.save(otra);
		Usuario duenoB = nuevoUsuario("wb-" + tag);
		Cliente clienteB = new Cliente();
		clienteB.setUsuario(duenoB);
		clienteB.setTienda(otra);
		clienteB.setAceptaMarketing(false);
		clienteB.setEmail(clienteB.getUsuario().getEmail());
		clientes.save(clienteB);
		Producto productoB = new Producto();
		productoB.setTienda(otra);
		productoB.setTitulo("B " + tag);
		productoB.setSlug("wsb-" + tag);
		productoB.setEstado(Producto.ACTIVO);
		productos.save(productoB);
		Resena ajena = new Resena();
		ajena.setProducto(productoB);
		ajena.setCliente(clienteB);
		ajena.setCalificacion(4);
		resenas.save(ajena);

		mvc.perform(patch("/api/admin/resenas/{id}/aprobar", ajena.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNotFound());
	}

	@Test
	void perfilLeeYActualizaParcial() throws Exception {
		String base = "/api/storefront/" + tienda.getSlug() + "/cuenta/perfil";
		mvc.perform(get(base)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aceptaMarketing").value(false));

		mvc.perform(patch(base)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Ana\",\"telefono\":\"999\",\"aceptaMarketing\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.telefono").value("999"))
				.andExpect(jsonPath("$.aceptaMarketing").value(true));

		mvc.perform(patch(base)
				.header("Authorization", "Bearer " + jwtCliente)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"apellido\":\"Paz\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.apellido").value("Paz"));
	}

	@Test
	void perfilSinTokenNoEntra() throws Exception {
		mvc.perform(get("/api/storefront/" + tienda.getSlug() + "/cuenta/perfil"))
				.andExpect(status().isForbidden());
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
