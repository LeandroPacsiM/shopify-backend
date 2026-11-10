package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.dto.CheckoutResponse;
import com.tienda.plataforma.venta.dto.CrearCheckoutRequest;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CarritoItem;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.MetodoEnvioRepository;
import com.tienda.plataforma.venta.service.PedidoService;

/** V17: el contacto del checkout se copia al pedido; sin contacto queda NULL. Rollback al final. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PedidoContactoTest {

	@Autowired
	PedidoService pedidos;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	ProductoRepository productos;
	@Autowired
	ProductoVarianteRepository variantes;
	@Autowired
	ArticuloInventarioRepository articulos;
	@Autowired
	DireccionRepository direcciones;
	@Autowired
	MetodoEnvioRepository metodos;
	@Autowired
	CarritoRepository carritos;
	@Autowired
	CarritoItemRepository carritoItems;
	@Autowired
	SuscripcionRepository suscripciones;

	@Test
	void contactoViajaAlPedido() {
		Contexto ctx = fixtures("pc-");
		var req = new CrearCheckoutRequest(ctx.carritoToken(), ctx.direccionId(), ctx.direccionId(),
				ctx.metodoId(), null, "cli@example.com", "999888777", "Dejar en puerta");
		CheckoutResponse checkout = pedidos.crearCheckout(ctx.clienteId(), ctx.slug(), req);
		assertEquals("cli@example.com", checkout.emailContacto());
		assertEquals("999888777", checkout.telefonoContacto());
		assertEquals("Dejar en puerta", checkout.notasCliente());

		PedidoResponse pedido = pedidos.confirmar(ctx.clienteId(), ctx.tiendaId(), checkout.id());
		assertEquals("cli@example.com", pedido.emailContacto());
		assertEquals("999888777", pedido.telefonoContacto());
		assertEquals("Dejar en puerta", pedido.notasCliente());
	}

	@Test
	void sinContactoQuedaNulo() {
		Contexto ctx = fixtures("pn-");
		var req = new CrearCheckoutRequest(ctx.carritoToken(), ctx.direccionId(), ctx.direccionId(),
				ctx.metodoId(), null);
		CheckoutResponse checkout = pedidos.crearCheckout(ctx.clienteId(), ctx.slug(), req);
		PedidoResponse pedido = pedidos.confirmar(ctx.clienteId(), ctx.tiendaId(), checkout.id());
		assertNull(pedido.emailContacto());
		assertNull(pedido.telefonoContacto());
		assertNull(pedido.notasCliente());
	}

	@Test
	void noConfirmaCheckoutConTenantDistinto() {
		Contexto ctx = fixtures("pt-");
		var req = new CrearCheckoutRequest(ctx.carritoToken(), ctx.direccionId(), ctx.direccionId(),
				ctx.metodoId(), null);
		CheckoutResponse checkout = pedidos.crearCheckout(ctx.clienteId(), ctx.slug(), req);

		Tienda otra = new Tienda();
		otra.setNombre("otra tienda " + UUID.randomUUID());
		otra.setSlug("otra-" + UUID.randomUUID());
		otra.setActiva(true);
		tiendas.save(otra);

		assertThrows(NoEncontradoException.class,
				() -> pedidos.confirmar(ctx.clienteId(), otra.getId(), checkout.id()));
	}

	@Test
	void noConfirmaCheckoutSiCarritoYaNoPerteneceAlCliente() {
		Contexto ctx = fixtures("po-");
		var req = new CrearCheckoutRequest(ctx.carritoToken(), ctx.direccionId(), ctx.direccionId(),
				ctx.metodoId(), null);
		CheckoutResponse checkout = pedidos.crearCheckout(ctx.clienteId(), ctx.slug(), req);
		Carrito carrito = carritos.findById(ctx.carritoId()).orElseThrow();
		carrito.setCliente(null);
		carritos.save(carrito);

		assertThrows(NoEncontradoException.class,
				() -> pedidos.confirmar(ctx.clienteId(), ctx.tiendaId(), checkout.id()));
	}

	@Test
	void noConfirmaCheckoutDeTiendaCongelada() {
		Contexto ctx = fixtures("pf-");
		var req = new CrearCheckoutRequest(ctx.carritoToken(), ctx.direccionId(), ctx.direccionId(),
				ctx.metodoId(), null);
		CheckoutResponse checkout = pedidos.crearCheckout(ctx.clienteId(), ctx.slug(), req);
		Tienda tienda = tiendas.findById(ctx.tiendaId()).orElseThrow();
		tienda.setActiva(false);
		tiendas.save(tienda);

		assertThrows(ConflictoException.class,
				() -> pedidos.confirmar(ctx.clienteId(), ctx.tiendaId(), checkout.id()));
	}

	private record Contexto(Long clienteId, Long tiendaId, Long carritoId, String slug,
			String carritoToken, Long direccionId, Long metodoId) {
	}

	private Contexto fixtures(String prefijo) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre(prefijo + tag);
		tienda.setSlug(prefijo + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario usuario = new Usuario();
		usuario.setEmail(prefijo + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		Cliente cliente = new Cliente();
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(usuario.getEmail());
		clientes.save(cliente);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo(prefijo + tag);
		producto.setSlug("p" + prefijo + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);

		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("PCSKU-" + tag);
		variante.setPrecio(new BigDecimal("30.00"));
		variante.setActivo(true);
		variantes.save(variante);

		ArticuloInventario articulo = new ArticuloInventario();
		articulo.setVariante(variante);
		articulo.setRequiereEnvio(false);
		articulo.setControlaStock(false);
		articulos.save(articulo);

		Direccion direccion = new Direccion();
		direccion.setCliente(cliente);
		direccion.setLinea1("Calle 1");
		direccion.setCiudad("Lima");
		direccion.setPais("PE");
		direcciones.save(direccion);

		MetodoEnvio metodo = new MetodoEnvio();
		metodo.setTienda(tienda);
		metodo.setNombre("Digital");
		metodo.setPrecio(BigDecimal.ZERO);
		metodo.setActivo(true);
		metodos.save(metodo);

		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("pctok-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);

		CarritoItem item = new CarritoItem();
		item.setCarrito(carrito);
		item.setVariante(variante);
		item.setCantidad(1);
		item.setPrecioUnitario(new BigDecimal("30.00"));
		carritoItems.save(item);

		Suscripcion suscripcion = new Suscripcion();
		suscripcion.setTienda(tienda);
		suscripcion.setPlan(Suscripcion.MENSUAL);
		suscripcion.setEstado(Suscripcion.ACTIVA);
		suscripcion.setPeriodoFin(java.time.Instant.now().plusSeconds(30 * 24 * 60 * 60));
		suscripciones.save(suscripcion);

		return new Contexto(cliente.getId(), tienda.getId(), carrito.getId(), tienda.getSlug(), carrito.getToken(),
				direccion.getId(), metodo.getId());
	}
}
