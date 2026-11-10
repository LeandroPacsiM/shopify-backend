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
import org.springframework.test.web.servlet.MvcResult;

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
import com.tienda.plataforma.tenants.repository.ClienteRepository;
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

/**
 * Dos compras concurrentes con 1 unidad de stock: solo una confirma
 * (suite AGENTS.md). Fixtures con commit (hilos paralelos no ven transacciones).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SobreventaTest {

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

	String jwt;
	String slug;
	Long checkoutA;
	Long checkoutB;
	Long nivelId;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = nuevaTienda(tag);
		slug = tienda.getSlug();
		Suscripcion suscripcion = new Suscripcion();
		suscripcion.setTienda(tienda);
		suscripcion.setPlan(Suscripcion.MENSUAL);
		suscripcion.setEstado(Suscripcion.ACTIVA);
		suscripcion.setPeriodoFin(java.time.Instant.now().plusSeconds(30L * 24 * 60 * 60));
		suscripciones.save(suscripcion);
		Usuario usuario = nuevoUsuario(tag);
		Cliente cliente = nuevoCliente(usuario, tienda);
		ProductoVariante variante = nuevoProducto(tag, tienda, "60.00");
		nivelId = nuevoNivel(tag, tienda, variante, 1);
		Direccion direccion = nuevaDireccion(cliente);
		MetodoEnvio metodo = nuevoMetodo(tienda);

		checkoutA = nuevoCheckout(tag + "a", tienda, cliente, variante, direccion, metodo, 1);
		checkoutB = nuevoCheckout(tag + "b", tienda, cliente, variante, direccion, metodo, 1);
		jwt = jwtService.accesoCliente(cliente.getId(), tienda.getId());
	}

	@Test
	void unaSolaCompraConStockUno() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Callable<Integer> a = () -> estado(confirmar(checkoutA));
		Callable<Integer> b = () -> estado(confirmar(checkoutB));
		List<Future<Integer>> resultados = pool.invokeAll(List.of(a, b));
		pool.shutdown();
		long ok = 0;
		for (Future<Integer> f : resultados) {
			if (f.get() == 200) {
				ok++;
			}
		}
		assertEquals(1, ok);
		assertEquals(1, niveles.findById(nivelId).orElseThrow().getReservado());
	}

	private MvcResult confirmar(Long checkoutId) throws Exception {
		return mvc.perform(post("/api/checkout/{id}/confirmar", checkoutId)
				.header("Authorization", "Bearer " + jwt)
				.header("X-Tienda-Slug", slug))
				.andReturn();
	}

	private int estado(MvcResult r) {
		return r.getResponse().getStatus();
	}

	private Tienda nuevaTienda(String tag) {
		Tienda tienda = new Tienda();
		tienda.setNombre("S-" + tag);
		tienda.setSlug("s-" + tag);
		tienda.setActiva(true);
		return tiendas.save(tienda);
	}

	private Usuario nuevoUsuario(String tag) {
		Usuario usuario = new Usuario();
		usuario.setEmail("s-" + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		return usuarios.save(usuario);
	}

	private Cliente nuevoCliente(Usuario usuario, Tienda tienda) {
		Cliente cliente = new Cliente();
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		return clientes.save(cliente);
	}

	private ProductoVariante nuevoProducto(String tag, Tienda tienda, String precio) {
		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("S-" + tag);
		producto.setSlug("sp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("SSKU-" + tag);
		variante.setPrecio(new BigDecimal(precio));
		variante.setActivo(true);
		return variantes.save(variante);
	}

	private Long nuevoNivel(String tag, Tienda tienda, ProductoVariante variante, int stock) {
		Ubicacion ubicacion = new Ubicacion();
		ubicacion.setTienda(tienda);
		ubicacion.setNombre("SU-" + tag);
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
		nivel.setDisponible(stock);
		nivel.setReservado(0);
		return niveles.save(nivel).getId();
	}

	private Direccion nuevaDireccion(Cliente cliente) {
		Direccion direccion = new Direccion();
		direccion.setCliente(cliente);
		direccion.setLinea1("Calle 1");
		direccion.setCiudad("Lima");
		direccion.setPais("PE");
		return direcciones.save(direccion);
	}

	private MetodoEnvio nuevoMetodo(Tienda tienda) {
		MetodoEnvio metodo = new MetodoEnvio();
		metodo.setTienda(tienda);
		metodo.setNombre("Estándar");
		metodo.setPrecio(BigDecimal.ZERO);
		metodo.setActivo(true);
		return metodos.save(metodo);
	}

	private Long nuevoCheckout(String tag, Tienda tienda, Cliente cliente, ProductoVariante variante,
			Direccion direccion, MetodoEnvio metodo, int cantidad) {
		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("stok-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);
		CarritoItem item = new CarritoItem();
		item.setCarrito(carrito);
		item.setVariante(variante);
		item.setCantidad(cantidad);
		item.setPrecioUnitario(variante.getPrecio());
		carritoItems.save(item);
		BigDecimal subtotal = variante.getPrecio().multiply(BigDecimal.valueOf(cantidad));
		CheckoutSesion checkout = new CheckoutSesion();
		checkout.setTienda(tienda);
		checkout.setCarrito(carrito);
		checkout.setCliente(cliente);
		checkout.setDireccionEnvio(direccion);
		checkout.setDireccionFacturacion(direccion);
		checkout.setMetodoEnvio(metodo);
		checkout.setMoneda("PEN");
		checkout.setSubtotal(subtotal);
		checkout.setDescuentoTotal(BigDecimal.ZERO);
		checkout.setImpuestoTotal(BigDecimal.ZERO);
		checkout.setEnvioTotal(BigDecimal.ZERO);
		checkout.setTotal(subtotal);
		checkout.setEstado(CheckoutSesion.PENDIENTE);
		return checkouts.save(checkout).getId();
	}
}
