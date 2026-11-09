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

import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/** FE-02: catálogo público por tienda, sin JWT y sin fuga entre tiendas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontCatalogoTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	TiendaRepository tiendas;
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
		tienda = new Tienda();
		tienda.setNombre("C-" + tag);
		tienda.setSlug("c-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

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
		nuevoProducto(otra, "Ajeno " + tag, "aje-" + tag, Producto.ACTIVO);
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
