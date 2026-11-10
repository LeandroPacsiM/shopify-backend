package com.tienda.plataforma.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.dto.CrearProductoRequest;
import com.tienda.plataforma.catalogo.dto.ProductoResponse;
import com.tienda.plataforma.catalogo.service.ProductoService;
import com.tienda.plataforma.shared.exception.SolicitudInvalidaException;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

/**
 * `GET /api/admin/productos?estado=`: el admin ve por estado (default
 * activos); estado inválido es 400. El storefront público sigue aparte.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductoEstadoFiltroTest {

	@Autowired
	ProductoService productos;
	@Autowired
	TiendaRepository tiendas;

	@Test
	void filtraPorEstadoYRechazaInvalido() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("EF " + tag);
		tienda.setSlug("ef-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		ProductoResponse borrador = productos.crear(tienda.getId(),
				new CrearProductoRequest("B " + tag, "ef-b-" + tag, null));
		ProductoResponse aPublicar = productos.crear(tienda.getId(),
				new CrearProductoRequest("P " + tag, "ef-p-" + tag, null));
		ProductoResponse aArchivar = productos.crear(tienda.getId(),
				new CrearProductoRequest("A " + tag, "ef-a-" + tag, null));
		productos.publicar(tienda.getId(), aPublicar.id());
		productos.archivar(tienda.getId(), aArchivar.id());

		assertEquals(1, productos.listar(tienda.getId(), "activo").size());
		assertEquals(1, productos.listar(tienda.getId(), "borrador").size());
		assertEquals(1, productos.listar(tienda.getId(), "archivado").size());
		assertEquals(1, productos.listar(tienda.getId(), null).size());
		assertEquals(borrador.id(), productos.listar(tienda.getId(), "borrador").get(0).id());
		assertThrows(SolicitudInvalidaException.class,
				() -> productos.listar(tienda.getId(), "derretido"));
	}
}
