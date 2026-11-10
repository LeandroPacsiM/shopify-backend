package com.tienda.plataforma.catalogo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/** FE-02: catálogo público por tienda, sin JWT y sin fuga entre tiendas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontCatalogoTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	PlatformTransactionManager txManager;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	TiendaUsuarioRepository vinculos;
	@Autowired
	com.tienda.plataforma.auth.repository.UsuarioRepository usuarios;
	@Autowired
	ProductoRepository productos;
	@Autowired
	ProductoVarianteRepository variantes;

	Tienda tienda;
	Producto visible;
	Producto borrador;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		// V22: todo en una sola TX (tiendas + owners; el trigger dispara al commit).
		new TransactionTemplate(txManager).executeWithoutResult(s -> {
			tienda = new Tienda();
			tienda.setNombre("C-" + tag);
			tienda.setSlug("c-" + tag);
			tienda.setActiva(true);
			tiendas.save(tienda);
			duenoDe(tienda, "co-" + tag);

			visible = nuevoProducto(tienda, "Camiseta " + tag, "cam-" + tag, Producto.ACTIVO);
			nuevaVariante(visible, "SKU-V-" + tag, "50.00", true);
			nuevaVariante(visible, "SKU-X-" + tag, "60.00", false);

			borrador = nuevoProducto(tienda, "Borrador " + tag, "bor-" + tag, Producto.BORRADOR);
			nuevaVariante(borrador, "SKU-B-" + tag, "10.00", true);

			Tienda otra = new Tienda();
			otra.setNombre("O-" + tag);
			otra.setSlug("o-" + tag);
			otra.setActiva(true);
			tiendas.save(otra);
			duenoDe(otra, "oo-" + tag);
			nuevoProducto(otra, "Ajeno " + tag, "aje-" + tag, Producto.ACTIVO);
		});
	}

	private void duenoDe(Tienda tienda, String tag) {
		com.tienda.plataforma.auth.entity.Usuario dueno =
				new com.tienda.plataforma.auth.entity.Usuario();
		dueno.setEmail(tag + "@example.com");
		dueno.setPasswordHash("x");
		dueno.setActivo(true);
		dueno.setEmailVerificado(true);
		dueno.setIntentosFallidos(0);
		usuarios.save(dueno);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(dueno);
		v.setRolTienda(TiendaUsuario.STORE_OWNER);
		vinculos.save(v);
	}

	@Test
	void listaSoloActivosDeLaTiendaSinJwt() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + visible.getSlug() + "')]").exists())
				.andExpect(jsonPath("$[?(@.slug=='" + borrador.getSlug() + "')]").doesNotExist())
				.andExpect(jsonPath("$[?(@.titulo=='Ajeno')]").doesNotExist());
	}

	@Test
	void busquedaFiltraPorTitulo() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug()).param("q", "camiseta"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.slug=='" + visible.getSlug() + "')]").exists());
		mvc.perform(get("/api/storefront/{slug}/productos", tienda.getSlug()).param("q", "zzz-sin-nada"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void detalleIncluyeSoloVariantesActivas() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/productos/{p}", tienda.getSlug(), visible.getSlug()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value(visible.getSlug()))
				.andExpect(jsonPath("$.variantes.length()").value(1))
				.andExpect(jsonPath("$.variantes[0].sku").value(visible.getSlug().replace("cam-", "SKU-V-")));
	}

	@Test
	void detalleDeOtraTiendaOInactivoEs404() throws Exception {
		mvc.perform(get("/api/storefront/{slug}/productos/{p}", tienda.getSlug(), borrador.getSlug()))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/storefront/{slug}/productos/{p}", tienda.getSlug(), "aje-no-existe"))
				.andExpect(status().isNotFound());
	}

	private Producto nuevoProducto(Tienda tienda, String titulo, String slug, String estado) {
		Producto p = new Producto();
		p.setTienda(tienda);
		p.setTitulo(titulo);
		p.setSlug(slug);
		p.setEstado(estado);
		return productos.save(p);
	}

	private void nuevaVariante(Producto producto, String sku, String precio, boolean activa) {
		ProductoVariante v = new ProductoVariante();
		v.setProducto(producto);
		v.setSku(sku);
		v.setPrecio(new BigDecimal(precio));
		v.setActivo(activa);
		variantes.save(v);
	}
}
