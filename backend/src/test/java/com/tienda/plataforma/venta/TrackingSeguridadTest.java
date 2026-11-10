package com.tienda.plataforma.venta;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/** RF-TENANT-02: sin fuga entre tiendas (diagrama 05, contrato en 10). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrackingSeguridadTest {

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
	PedidoRepository pedidos;

	Tienda tiendaDos;
	Usuario dueno;
	Pedido pedidoAjeno;
	String jwtDueno;

	private Tienda tiendaDemo() {
		return tiendas.findBySlug("tienda-demo").orElseGet(() -> {
			Tienda t = new Tienda();
			t.setNombre("Tienda Demo");
			t.setSlug("tienda-demo");
			t.setActiva(true);
			return tiendas.save(t);
		});
	}

	@BeforeEach
	void fixtures() {
		tiendaDemo();
		tiendaDos = new Tienda();
		tiendaDos.setNombre("Tienda Dos Test");
		tiendaDos.setSlug("tienda-dos-test-" + UUID.randomUUID().toString().substring(0, 8));
		tiendaDos.setActiva(true);
		tiendas.save(tiendaDos);

		dueno = new Usuario();
		dueno.setEmail("dueno-" + UUID.randomUUID() + "@example.com");
		dueno.setPasswordHash("x");
		dueno.setActivo(true);
		dueno.setEmailVerificado(true);
		dueno.setIntentosFallidos(0);
		usuarios.save(dueno);

		Cliente cliente = new Cliente();
		cliente.setTienda(tiendaDos);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(dueno.getEmail());
		clientes.save(cliente);

		pedidoAjeno = new Pedido();
		pedidoAjeno.setTienda(tiendaDos);
		pedidoAjeno.setCliente(cliente);
		pedidoAjeno.setNumeroPedido("T-" + UUID.randomUUID().toString().substring(0, 8));
		pedidoAjeno.setEstado(Pedido.PAGADO);
		pedidos.save(pedidoAjeno);

		// F4b: sin rol global `cliente` (V23); el JWT CUSTOMER manda.
		jwtDueno = jwtService.accesoCliente(cliente.getId(), tiendaDos.getId());
	}

	@Test
	void duenoVeSuPedido() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/cuenta/pedidos/{id}/tracking",
				tiendaDos.getSlug(), pedidoAjeno.getId())
				.header("Authorization", "Bearer " + jwtDueno)
				.header("X-Tienda-Slug", tiendaDos.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.numeroPedido").value(pedidoAjeno.getNumeroPedido()));
	}

	@Test
	void pedidoDeOtraTiendaNoExiste() throws Exception {
		// El TenantFilter bloquea (403) sin llegar al servicio: no filtra existencia.
		mvc.perform(get("/api/storefront/tienda-demo/cuenta/pedidos/{id}/tracking", pedidoAjeno.getId())
				.header("Authorization", "Bearer " + jwtDueno)
				.header("X-Tienda-Slug", "tienda-demo"))
				.andExpect(status().isForbidden());
	}

	@Test
	void sinTokenNoEntra() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/cuenta/pedidos/{id}/tracking",
				tiendaDos.getSlug(), pedidoAjeno.getId()))
				.andExpect(status().isForbidden());
	}

	@Test
	void headerDeOtraTiendaSeBloquea() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/cuenta/pedidos/{id}/tracking",
				tiendaDos.getSlug(), pedidoAjeno.getId())
				.header("Authorization", "Bearer " + jwtDueno)
				.header("X-Tienda-Slug", "tienda-demo"))
				.andExpect(status().isForbidden());
	}
}
