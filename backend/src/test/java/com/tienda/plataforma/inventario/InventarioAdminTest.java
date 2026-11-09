package com.tienda.plataforma.inventario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** RF-ADMIN-02: movimientos atómicos con bloqueo y sin fuga entre tiendas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InventarioAdminTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository tiendaUsuarios;
	@Autowired
	ProductoRepository productos;
	@Autowired
	ProductoVarianteRepository variantes;

	Tienda tienda;
	ProductoVariante variante;
	String jwtAdmin;
	Long ubicacionId;

	@BeforeEach
	void fixtures() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("I-" + tag);
		tienda.setSlug("i-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario admin = nuevoUsuario("ia-" + tag);
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(admin);
		vinculo.setRolTienda(TiendaUsuario.ADMIN);
		tiendaUsuarios.save(vinculo);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("Inv " + tag);
		producto.setSlug("ivp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
		variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("IVSKU-" + tag);
		variante.setPrecio(new BigDecimal("20.00"));
		variante.setActivo(true);
		variantes.save(variante);

		String resp = mvc.perform(post("/api/admin/inventario/ubicaciones")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Central\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Central"))
				.andReturn().getResponse().getContentAsString();
		ubicacionId = ((Number) com.jayway.jsonpath.JsonPath.read(resp, "$.id")).longValue();
	}

	@Test
	void entradaCreaNivelSalidaYAjusteMuevenConTope() throws Exception {
		mover("entrada", 10);
		nivel(10, 0);

		mover("salida", 6);
		nivel(4, 0);

		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo("salida", 5)))
				.andExpect(status().isConflict());

		mover("ajuste", -2);
		nivel(2, 0);

		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo("ajuste", -3)))
				.andExpect(status().isConflict());
	}

	@Test
	void reservaPorApiSeRechazaYCruzarTiendasNoExiste() throws Exception {
		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo("reserva", 1)))
				.andExpect(status().isConflict());

		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda otra = new Tienda();
		otra.setNombre("IO-" + tag);
		otra.setSlug("io-" + tag);
		otra.setActiva(true);
		tiendas.save(otra);
		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", otra.getSlug()))
				.andExpect(status().isForbidden());

		Producto productoB = new Producto();
		productoB.setTienda(otra);
		productoB.setTitulo("B " + tag);
		productoB.setSlug("ivb-" + tag);
		productoB.setEstado(Producto.ACTIVO);
		productos.save(productoB);
		ProductoVariante varianteB = new ProductoVariante();
		varianteB.setProducto(productoB);
		varianteB.setSku("IVB-" + tag);
		varianteB.setPrecio(new BigDecimal("5.00"));
		varianteB.setActivo(true);
		variantes.save(varianteB);
		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"varianteId\":" + varianteB.getId() + ",\"ubicacionId\":" + ubicacionId
						+ ",\"tipo\":\"entrada\",\"cantidad\":1}"))
				.andExpect(status().isNotFound());
	}

	private void mover(String tipo, int cantidad) throws Exception {
		mvc.perform(post("/api/admin/inventario/movimientos")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(tipo, cantidad)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ubicacionId").value(ubicacionId));
	}

	private void nivel(int disponible, int reservado) throws Exception {
		mvc.perform(get("/api/admin/inventario/niveles")
				.param("varianteId", variante.getId().toString())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].disponible").value(disponible))
				.andExpect(jsonPath("$[0].reservado").value(reservado));
	}

	private String cuerpo(String tipo, int cantidad) {
		return "{\"varianteId\":" + variante.getId() + ",\"ubicacionId\":" + ubicacionId
				+ ",\"tipo\":\"" + tipo + "\",\"cantidad\":" + cantidad + "}";
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
