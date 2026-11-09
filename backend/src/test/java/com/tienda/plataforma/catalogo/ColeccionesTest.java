package com.tienda.plataforma.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** Colecciones: admin asigna productos propios; el público filtra por tienda. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ColeccionesTest {

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
	ProductoRepository productos;
	@Autowired
	ProductoVarianteRepository variantes;

	Tienda tienda;
	Producto producto;
	String jwtAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("K-" + tag);
		tienda.setSlug("k-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario admin = nuevoUsuario("ka-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(admin);
		v.setRolTienda(TiendaUsuario.ADMIN);
		vinculos.save(v);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("Colec " + tag);
		producto.setSlug("ksp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("KSKU-" + tag);
		variante.setPrecio(new BigDecimal("30.00"));
		variante.setActivo(true);
		variantes.save(variante);
	}

	@Test
	void adminCreaAsignaPublicaYQuita() throws Exception {
		String slugCol = "col-" + UUID.randomUUID().toString().substring(0, 8);
		String creada = mvc.perform(post("/api/admin/colecciones")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\" (" + slugCol + ")\",\"slug\":\"" + slugCol + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long coleccionId = ((Number) com.jayway.jsonpath.JsonPath.read(creada, "$.id")).longValue();
		mvc.perform(post("/api/admin/colecciones")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Otra\",\"slug\":\"" + slugCol + "\"}"))
				.andExpect(status().isConflict());

		String asignar = "{\"productoId\":" + producto.getId() + ",\"posicion\":1}";
		mvc.perform(post("/api/admin/colecciones/{id}/productos", coleccionId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(asignar))
				.andExpect(status().isOk());
		mvc.perform(post("/api/admin/colecciones/{id}/productos", coleccionId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(asignar))
				.andExpect(status().isConflict());

		mvc.perform(get("/api/storefront/{slug}/colecciones", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + slugCol + "')]").exists());
		mvc.perform(get("/api/storefront/{slug}/colecciones/{c}/productos",
				tienda.getSlug(), slugCol))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].slug").value(producto.getSlug()));

		mvc.perform(delete("/api/admin/colecciones/{id}/productos/{p}", coleccionId, producto.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/storefront/{slug}/colecciones", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + slugCol + "')]").doesNotExist());
	}

	@Test
	void asignarProductoAjenoYVerColeccionAjenaNoExiste() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda otra = new Tienda();
		otra.setNombre("KO-" + tag);
		otra.setSlug("ko-" + tag);
		otra.setActiva(true);
		tiendas.save(otra);
		Producto ajeno = new Producto();
		ajeno.setTienda(otra);
		ajeno.setTitulo("Ajeno " + tag);
		ajeno.setSlug("kso-" + tag);
		ajeno.setEstado(Producto.ACTIVO);
		productos.save(ajeno);

		String slugCol = "colx-" + tag;
		String creada = mvc.perform(post("/api/admin/colecciones")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"X\",\"slug\":\"" + slugCol + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long coleccionId = ((Number) com.jayway.jsonpath.JsonPath.read(creada, "$.id")).longValue();

		mvc.perform(post("/api/admin/colecciones/{id}/productos", coleccionId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productoId\":" + ajeno.getId() + "}"))
				.andExpect(status().isNotFound());

		mvc.perform(patch("/api/admin/colecciones/{id}", coleccionId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"activo\":false}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/storefront/{slug}/colecciones/{c}/productos",
				tienda.getSlug(), slugCol))
				.andExpect(status().isNotFound());
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
