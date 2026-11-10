package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** RNF-01: el listado admin se pagina en BD (sin traer todo a memoria). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PedidoPaginacionTest {

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
	TiendaUsuarioRepository vinculos;
	@Autowired
	PedidoRepository pedidos;

	Tienda tienda;
	String jwtAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("P-" + tag);
		tienda.setSlug("p-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario admin = nuevoUsuario("pa-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(admin);
		v.setRolTienda(TiendaUsuario.ADMIN);
		vinculos.save(v);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		Usuario dueno = nuevoUsuario("pc-" + tag);
		Cliente cliente = new Cliente();
		cliente.setUsuario(dueno);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);

		for (int i = 0; i < 5; i++) {
			Pedido p = new Pedido();
			p.setTienda(tienda);
			p.setCliente(cliente);
			p.setNumeroPedido("PG-" + tag + "-" + i);
			p.setEstado(i < 2 ? Pedido.PAGADO : Pedido.PENDIENTE);
			p.setMoneda("PEN");
			p.setSubtotal(BigDecimal.TEN);
			p.setImpuestoTotal(BigDecimal.ZERO);
			p.setEnvioTotal(BigDecimal.ZERO);
			p.setDescuentoTotal(BigDecimal.ZERO);
			p.setTotal(BigDecimal.TEN);
			pedidos.save(p);
		}
	}

	@Test
	void paginaTamanoYFiltroPorEstado() throws Exception {
		String base = "/api/admin/pedidos";
		mvc.perform(get(base)
				.param("page", "0").param("size", "2")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3))
				.andExpect(jsonPath("$.content.length()").value(2));

		mvc.perform(get(base)
				.param("page", "2").param("size", "2")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1));

		mvc.perform(get(base)
				.param("estado", "pagado")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		mvc.perform(get(base)
				.param("size", "500")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.content.length()").value(5));
	}

	@Test
	void ordenDescendenteEstable() throws Exception {
		String r1 = mvc.perform(get("/api/admin/pedidos")
				.param("size", "5")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		List<Integer> ids = com.jayway.jsonpath.JsonPath.read(r1, "$.content[*].id");
		assertEquals(5, ids.size());
		assertTrue(ids.get(0) > ids.get(4));
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
