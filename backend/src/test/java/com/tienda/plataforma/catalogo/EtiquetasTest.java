package com.tienda.plataforma.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** Etiquetas: admin asigna a productos propios; el público filtra por tienda. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EtiquetasTest {

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

	Tienda tienda;
	Producto producto;
	String jwtAdmin;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("G-" + tag);
		tienda.setSlug("g-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario admin = nuevoUsuario("ga-" + tag);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(admin);
		v.setRolTienda(TiendaUsuario.ADMIN);
		vinculos.save(v);
		jwtAdmin = jwtService.acceso(admin.getId(), List.of("admin"));

		producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("Tag " + tag);
		producto.setSlug("gtp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
	}

	@Test
	void adminCreaAsignaFiltraYQuita() throws Exception {
		String slugTag = "tag-" + UUID.randomUUID().toString().substring(0, 8);
		String creada = mvc.perform(post("/api/admin/etiquetas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Oferta\",\"slug\":\"" + slugTag + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(slugTag))
				.andReturn().getResponse().getContentAsString();
		long etiquetaId = ((Number) com.jayway.jsonpath.JsonPath.read(creada, "$.id")).longValue();
		mvc.perform(post("/api/admin/etiquetas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Otra\",\"slug\":\"" + slugTag + "\"}"))
				.andExpect(status().isConflict());

		String asignar = "{\"productoId\":" + producto.getId() + "}";
		mvc.perform(post("/api/admin/etiquetas/{id}/productos", etiquetaId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(asignar))
				.andExpect(status().isOk());
		mvc.perform(post("/api/admin/etiquetas/{id}/productos", etiquetaId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content(asignar))
				.andExpect(status().isConflict());

		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug())
				.param("etiqueta", slugTag))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug())
				.param("etiqueta", "inexistente"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(get("/api/storefront/{slug}/etiquetas", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + slugTag + "')]").exists());
		mvc.perform(get("/api/storefront/{slug}/productos/{p}", tienda.getSlug(), producto.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.etiquetas[?(@=='" + slugTag + "')]").exists());

		mvc.perform(delete("/api/admin/etiquetas/{id}/productos/{p}", etiquetaId, producto.getId())
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/storefront/{slug}/etiquetas", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + slugTag + "')]").doesNotExist());
	}

	@Test
	void asignarProductoAjenoNoExiste() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda otra = new Tienda();
		otra.setNombre("GO-" + tag);
		otra.setSlug("go-" + tag);
		otra.setActiva(true);
		tiendas.save(otra);
		Producto ajeno = new Producto();
		ajeno.setTienda(otra);
		ajeno.setTitulo("Ajeno " + tag);
		ajeno.setSlug("gto-" + tag);
		ajeno.setEstado(Producto.ACTIVO);
		productos.save(ajeno);

		String creada = mvc.perform(post("/api/admin/etiquetas")
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"X\",\"slug\":\"tagx-" + tag + "\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long etiquetaId = ((Number) com.jayway.jsonpath.JsonPath.read(creada, "$.id")).longValue();

		mvc.perform(post("/api/admin/etiquetas/{id}/productos", etiquetaId)
				.header("Authorization", "Bearer " + jwtAdmin)
				.header("X-Tienda-Slug", tienda.getSlug())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productoId\":" + ajeno.getId() + "}"))
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
