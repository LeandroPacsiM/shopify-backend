package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.entity.Ubicacion;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.inventario.repository.UbicacionRepository;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CarritoItem;
import com.tienda.plataforma.venta.entity.CheckoutSesion;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.MetodoEnvioRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

import jakarta.persistence.EntityManager;

/** Doble confirmación concurrente crea un solo pedido (suite AGENTS.md). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConfirmacionConcurrenteTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	PlatformTransactionManager txManager;
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
	ProductoRepository productos;
	@Autowired
	ProductoVarianteRepository variantes;
	@Autowired
	UbicacionRepository ubicaciones;
	@Autowired
	ArticuloInventarioRepository articulos;
	@Autowired
	NivelInventarioRepository niveles;
	@Autowired
	DireccionRepository direcciones;
	@Autowired
	MetodoEnvioRepository metodos;
	@Autowired
	CarritoRepository carritos;
	@Autowired
	CarritoItemRepository carritoItems;
	@Autowired
	CheckoutSesionRepository checkouts;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	SuscripcionRepository suscripciones;
	@Autowired
	EntityManager entityManager;

	String jwt;
	String slug;
	Long checkoutId;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		// V22: todo en una sola TX commiteada (los hilos no ven transacciones).
		new TransactionTemplate(txManager).executeWithoutResult(s -> {
			Tienda tienda = new Tienda();
			tienda.setNombre("T-" + tag);
			tienda.setSlug("t-" + tag);
			tienda.setActiva(true);
			tiendas.save(tienda);
			slug = tienda.getSlug();
		Suscripcion suscripcion = new Suscripcion();
		suscripcion.setTienda(tienda);
		suscripcion.setPlan(Suscripcion.MENSUAL);
		suscripcion.setEstado(Suscripcion.ACTIVA);
		suscripcion.setPeriodoFin(java.time.Instant.now().plusSeconds(30L * 24 * 60 * 60));
		suscripciones.save(suscripcion);

		Usuario usuario = new Usuario();
		usuario.setEmail("c-" + tag + "@example.com");
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

		// V22: owner en la misma TX del fixture (commitea junto a la tienda).
		Usuario dueno = new Usuario();
		dueno.setEmail("to-" + tag + "@example.com");
		dueno.setPasswordHash("x");
		dueno.setActivo(true);
		dueno.setEmailVerificado(true);
		dueno.setTotpHabilitado(false);
		dueno.setIntentosFallidos(0);
		usuarios.save(dueno);
		TiendaUsuario v = new TiendaUsuario();
		v.setTienda(tienda);
		v.setUsuario(dueno);
		v.setRolTienda(TiendaUsuario.STORE_OWNER);
		vinculos.save(v);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("P-" + tag);
		producto.setSlug("p-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);

		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("SKU-" + tag);
		variante.setPrecio(new BigDecimal("50.00"));
		variante.setActivo(true);
		variantes.save(variante);

		Ubicacion ubicacion = new Ubicacion();
		ubicacion.setTienda(tienda);
		ubicacion.setNombre("A-" + tag);
		ubicacion.setActivo(true);
		ubicaciones.save(ubicacion);

		ArticuloInventario articulo = new ArticuloInventario();
		articulo.setVariante(variante);
		articulo.setRequiereEnvio(true);
		articulo.setControlaStock(true);
		articulos.save(articulo);

		NivelInventario nivel = new NivelInventario();
		nivel.setArticulo(articulo);
		nivel.setUbicacion(ubicacion);
		nivel.setDisponible(10);
		nivel.setReservado(0);
		niveles.save(nivel);

		Direccion direccion = new Direccion();
		direccion.setCliente(cliente);
		direccion.setLinea1("Calle 1");
		direccion.setCiudad("Lima");
		direccion.setPais("PE");
		direcciones.save(direccion);

		MetodoEnvio metodo = new MetodoEnvio();
		metodo.setTienda(tienda);
		metodo.setNombre("Estándar");
		metodo.setPrecio(new BigDecimal("10.00"));
		metodo.setActivo(true);
		metodos.save(metodo);

		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("tok-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);

		CarritoItem item = new CarritoItem();
		item.setCarrito(carrito);
		item.setVariante(variante);
		item.setCantidad(2);
		item.setPrecioUnitario(new BigDecimal("50.00"));
		carritoItems.save(item);

		CheckoutSesion checkout = new CheckoutSesion();
		checkout.setTienda(tienda);
		checkout.setCarrito(carrito);
		checkout.setCliente(cliente);
		checkout.setDireccionEnvio(direccion);
		checkout.setDireccionFacturacion(direccion);
		checkout.setMetodoEnvio(metodo);
		checkout.setMoneda("PEN");
		checkout.setSubtotal(new BigDecimal("100.00"));
		checkout.setDescuentoTotal(BigDecimal.ZERO);
		checkout.setImpuestoTotal(BigDecimal.ZERO);
		checkout.setEnvioTotal(new BigDecimal("10.00"));
		checkout.setTotal(new BigDecimal("110.00"));
		checkout.setEstado(CheckoutSesion.PENDIENTE);
		checkouts.save(checkout);
		checkoutId = checkout.getId();

		jwt = jwtService.accesoCliente(cliente.getId(), tienda.getId());
		});
	}

	@Test
	void dobleConfirmacionCreaUnSoloPedido() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Callable<Integer> llamada = () -> mvc.perform(post("/api/checkout/{id}/confirmar", checkoutId)
				.header("Authorization", "Bearer " + jwt)
				.header("X-Tienda-Slug", slug))
				.andReturn().getResponse().getStatus();
		List<Future<Integer>> resultados = pool.invokeAll(List.of(llamada, llamada));
		pool.shutdown();
		var estados = new java.util.ArrayList<Integer>();
		for (Future<Integer> f : resultados) {
			try {
				estados.add(f.get());
			} catch (Exception e) {
				estados.add(500);
			}
		}
		long creados = estados.stream().filter(s -> s == 200).count();
		assertEquals(1, creados, "estados: " + estados);
		assertEquals(1, pedidos.findByCheckoutSesionId(checkoutId).stream().count());
	}
}
