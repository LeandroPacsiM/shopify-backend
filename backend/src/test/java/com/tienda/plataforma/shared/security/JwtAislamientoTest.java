package com.tienda.plataforma.shared.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * Audiencias disjuntas (ADR-016, condición 6 v5): ningún JWT abre la
 * superficie del otro mundo. Con flag on.
 */
@SpringBootTest(properties = "auth.customer.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAislamientoTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;

	Tienda tienda;
	String jwtStaffComprador;
	String jwtCustomer;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("JA " + tag);
		tienda.setSlug("ja-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario comprador = new Usuario();
		comprador.setEmail("jac-" + tag + "@example.com");
		comprador.setPasswordHash("x");
		comprador.setActivo(true);
		comprador.setEmailVerificado(true);
		usuarios.save(comprador);
		Cliente cliente = new Cliente();
		cliente.setUsuario(comprador);
		cliente.setTienda(tienda);
		cliente.setEmail(comprador.getEmail());
		cliente.setAceptaMarketing(false);
		clientes.save(cliente);
		jwtStaffComprador = jwtService.acceso(comprador.getId(), List.of("cliente"));
		jwtCustomer = jwtService.accesoCliente(cliente.getId(), tienda.getId());
	}

	@Test
	void customerAbreCuentaPeroNoAdminYStaffNoAbreCuenta() throws Exception {
		mvc.perform(get("/api/admin/productos")
				.header("Authorization", "Bearer " + jwtCustomer)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isForbidden());
		// F3: el JWT CUSTOMER resuelve cuenta en su tienda.
		mvc.perform(get("/api/storefront/" + tienda.getSlug() + "/cuenta/perfil")
				.header("Authorization", "Bearer " + jwtCustomer))
				.andExpect(status().isOk());
		// Un JWT staff (aunque sea del comprador legacy) ya no abre cuenta.
		mvc.perform(get("/api/storefront/" + tienda.getSlug() + "/cuenta/perfil")
				.header("Authorization", "Bearer " + jwtStaffComprador))
				.andExpect(status().isForbidden());
	}

	@Test
	void verificadoresRechazanAudienciaAjena() {
		assertThrows(NoAutorizadoException.class,
				() -> jwtService.verificarCliente(jwtStaffComprador));
		assertThrows(NoAutorizadoException.class,
				() -> jwtService.verificarStaff(jwtCustomer));
	}
}
