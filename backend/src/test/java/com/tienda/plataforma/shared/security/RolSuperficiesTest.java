package com.tienda.plataforma.shared.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.auth.service.AuthService;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.repository.CarritoRepository;

/** RF-AUTH-08/RF-TENANT-03: rol de tienda y rol global no se confunden. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolSuperficiesTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	AuthService authService;
	@Autowired
	JwtService jwtService;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	CarritoRepository carritos;

	Tienda tienda;
	Tienda otraTienda;
	Usuario admin;
	Usuario operador;
	Usuario cliente;
	Cliente registroCliente;
	Usuario superAdmin;
	String jwtAdmin;
	String jwtOperador;
	String jwtCliente;
	String jwtSuperAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = nuevaTienda("rs-" + tag);
		otraTienda = nuevaTienda("ro-" + tag);

		admin = nuevoUsuario("rsa-" + tag);
		vincular(admin, TiendaUsuario.ADMIN);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		operador = nuevoUsuario("rso-" + tag);
		vincular(operador, TiendaUsuario.OPERADOR);
		jwtOperador = jwtService.acceso(operador.getId(), List.of("operador"));

		cliente = nuevoUsuario("rsc-" + tag);
		registroCliente = new Cliente();
		registroCliente.setTienda(tienda);
		registroCliente.setAceptaMarketing(false);
		registroCliente.setEmail(cliente.getEmail());
		clientes.save(registroCliente);
		// F3: el comprador entra con JWT CUSTOMER (aud propia), no con JWT staff.
		jwtCliente = jwtService.accesoCliente(registroCliente.getId(), tienda.getId());

		superAdmin = nuevoUsuario("rss-" + tag);
		jwtSuperAdmin = jwtService.acceso(superAdmin.getId(), List.of("super_admin"));
	}

	@Test
	void adminDeSuTiendaPuedeAdministrarYResetearPeroNoComprar() throws Exception {
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk());

		mvc.perform(post("/api/admin/usuarios/{id}/2fa/reset", operador.getId())
				.header("Authorization", bearer(jwtAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", bearer(jwtAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());
	}

	@Test
	void operadorAdministraPeroNoReseteaDosFa() throws Exception {
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtOperador))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk());

		mvc.perform(post("/api/admin/usuarios/{id}/2fa/reset", operador.getId())
				.header("Authorization", bearer(jwtOperador))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/pasarelas")
				.header("Authorization", bearer(jwtOperador))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/facturacion")
				.header("Authorization", bearer(jwtOperador))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		assertThrows(ProhibidoException.class,
				() -> authService.resetTotpPorAdmin(operador.getId(), operador.getId(), tienda.getId()));
	}

	@Test
	void customerNoAbreAdminNiOps() throws Exception {
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtCliente))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", bearer(jwtAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/admin/usuarios/{id}/2fa/reset", cliente.getId())
				.header("Authorization", bearer(jwtCliente))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", bearer(jwtCliente)))
				.andExpect(status().isNotFound());
	}

	@Test
	void clienteDeLaTiendaPuedeVerSuCuentaYConfirmarCheckoutPropio() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/cuenta/pedidos", tienda.getSlug())
				.header("Authorization", bearer(jwtCliente)))
				.andExpect(status().isOk());

		// Llega al servicio y falla solo porque el checkout de prueba no existe (404, no 403).
		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", bearer(jwtCliente))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNotFound());
	}

	@Test
	void carritoAnonimoSiguePublicoSinAbrirCheckoutInvitado() throws Exception {
		mvc.perform(post("/api/storefront/{slug}/carrito", tienda.getSlug()))
				.andExpect(status().isOk());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L))
				.andExpect(status().isForbidden());
	}

	@Test
	void slugDeStorefrontDebeCoincidirConElHeaderSiSeEnvia() throws Exception {
		mvc.perform(post("/api/storefront/{slug}/carrito", tienda.getSlug())
				.header("X-Tienda-Slug", otraTienda.getSlug()))
				.andExpect(status().isForbidden());
	}

	@Test
	void tokenDeCarritoNoPermiteCruzarTiendas() throws Exception {
		Carrito carrito = nuevoCarrito(tienda, "cross-" + UUID.randomUUID());

		mvc.perform(post("/api/storefront/{slug}/carrito", otraTienda.getSlug())
				.header("X-Carrito-Token", carrito.getToken()))
				.andExpect(status().isNotFound());
	}

	@Test
	void tokenNoPermiteLeerCarritoDeOtroClienteAutenticado() throws Exception {
		Usuario otroUsuario = nuevoUsuario("rsc2-" + UUID.randomUUID().toString().substring(0, 8));
		Cliente otroCliente = new Cliente();
		otroCliente.setTienda(tienda);
		otroCliente.setAceptaMarketing(false);
		otroCliente.setEmail(otroUsuario.getEmail());
		clientes.save(otroCliente);
		String jwtOtroCliente = jwtService.accesoCliente(otroCliente.getId(), tienda.getId());
		Carrito carrito = nuevoCarrito(tienda, registroCliente);

		mvc.perform(post("/api/storefront/{slug}/carrito", tienda.getSlug())
				.header("Authorization", bearer(jwtOtroCliente))
				.header("X-Carrito-Token", carrito.getToken()))
				.andExpect(status().isNotFound());
	}

	@Test
	void superAdminSoloEnOpsNoEnSuperficiesDeTienda() throws Exception {
		mvc.perform(get("/api/ops/tiendas")
				.header("Authorization", bearer(jwtSuperAdmin)))
				.andExpect(status().isOk());

		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtSuperAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/storefront/{slug}/cuenta/pedidos", tienda.getSlug())
				.header("Authorization", bearer(jwtSuperAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", bearer(jwtSuperAdmin))
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());
	}

	@Test
	void faltaTenantOEsDeOtraTiendaNoAutorizaAlAdmin() throws Exception {
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtAdmin)))
				.andExpect(status().isForbidden());

		mvc.perform(get("/api/admin/productos")
				.header("Authorization", bearer(jwtAdmin))
				.header("X-Tienda-Slug", otraTienda.getSlug()))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/checkout/{id}/confirmar", 999999L)
				.header("Authorization", bearer(jwtCliente)))
				.andExpect(status().isForbidden());
	}

	private Carrito nuevoCarrito(Tienda tienda, String token) {
		return nuevoCarrito(tienda, null, token);
	}

	private Carrito nuevoCarrito(Tienda tienda, Cliente cliente) {
		return nuevoCarrito(tienda, cliente, "cart-" + UUID.randomUUID());
	}

	private Carrito nuevoCarrito(Tienda tienda, Cliente cliente, String token) {
		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken(token);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		return carritos.save(carrito);
	}

	private Tienda nuevaTienda(String slug) {
		Tienda nueva = new Tienda();
		nueva.setNombre(slug);
		nueva.setSlug(slug);
		nueva.setActiva(true);
		return tiendas.save(nueva);
	}

	private Usuario nuevoUsuario(String email) {
		Usuario nuevo = new Usuario();
		nuevo.setEmail(email + "@example.com");
		nuevo.setPasswordHash("test-hash");
		nuevo.setActivo(true);
		nuevo.setEmailVerificado(true);
		nuevo.setIntentosFallidos(0);
		return usuarios.save(nuevo);
	}

	private void vincular(Usuario usuario, String rol) {
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(usuario);
		vinculo.setRolTienda(rol);
		tiendaUsuarios.save(vinculo);
	}

	private String bearer(String jwt) {
		return "Bearer " + jwt;
	}
}
