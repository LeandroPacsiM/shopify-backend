package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
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
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.MetodoEnvioRepository;
import com.tienda.plataforma.venta.repository.PedidoDescuentoRepository;

/**
 * Cupón con 1 uso restante y 2 confirmaciones concurrentes: solo una lo
 * consume (suite AGENTS.md). El lock pesimista del descuento serializa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CuponUltimoUsoTest {

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
	CheckoutSesionRepository checkouts;
	@Autowired
	DescuentoRepository descuentos;
	@Autowired
	PedidoDescuentoRepository pedidoDescuentos;
	@Autowired
	SuscripcionRepository suscripciones;

	String jwt;
	String slug;
	Long checkoutA;
	Long checkoutB;
	Long descuentoId;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("C-" + tag);
		tienda.setSlug("c-" + tag);
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
		cliente.setUsuario(usuario);
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(cliente.getUsuario().getEmail());
		clientes.save(cliente);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("C-" + tag);
		producto.setSlug("cp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);

		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("CSKU-" + tag);
		variante.setPrecio(new BigDecimal("100.00"));
		variante.setActivo(true);
		variantes.save(variante);

		// Sin ficha de inventario: no controla stock.
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

		Descuento descuento = new Descuento();
		descuento.setTienda(tienda);
		descuento.setCodigo("ULTIMO-" + tag);
		descuento.setTipo(Descuento.PORCENTAJE);
		descuento.setValor(new BigDecimal("10.00"));
		descuento.setUsoMaximo(1);
		descuento.setUsosActuales(0);
		descuento.setActivo(true);
		descuentos.save(descuento);
		descuentoId = descuento.getId();

		checkoutA = nuevoCheckout(tag + "a", tienda, cliente, variante, direccion, metodo, descuento);
		checkoutB = nuevoCheckout(tag + "b", tienda, cliente, variante, direccion, metodo, descuento);
		jwt = jwtService.accesoCliente(cliente.getId(), tienda.getId());
	}

	@Test
	void unSoloUsoConDobleConfirmacion() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Callable<Integer> a = () -> estado(checkoutA);
		Callable<Integer> b = () -> estado(checkoutB);
		List<Future<Integer>> resultados = pool.invokeAll(List.of(a, b));
		pool.shutdown();
		long ok = 0;
		for (Future<Integer> f : resultados) {
			if (f.get() == 200) {
				ok++;
			}
		}
		assertEquals(1, ok);
		assertEquals(Integer.valueOf(1),
				descuentos.findById(descuentoId).orElseThrow().getUsosActuales());
		assertEquals(1, pedidoDescuentos.findAll().stream()
				.filter(pd -> pd.getDescuento().getId().equals(descuentoId)).count());
	}

	private int estado(Long checkoutId) throws Exception {
		return mvc.perform(post("/api/checkout/{id}/confirmar", checkoutId)
				.header("Authorization", "Bearer " + jwt)
				.header("X-Tienda-Slug", slug))
				.andReturn().getResponse().getStatus();
	}

	private Long nuevoCheckout(String tag, Tienda tienda, Cliente cliente, ProductoVariante variante,
			Direccion direccion, MetodoEnvio metodo, Descuento descuento) {
		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("ctok-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);
		CarritoItem item = new CarritoItem();
		item.setCarrito(carrito);
		item.setVariante(variante);
		item.setCantidad(1);
		item.setPrecioUnitario(new BigDecimal("100.00"));
		carritoItems.save(item);
		CheckoutSesion checkout = new CheckoutSesion();
		checkout.setTienda(tienda);
		checkout.setCarrito(carrito);
		checkout.setCliente(cliente);
		checkout.setDireccionEnvio(direccion);
		checkout.setDireccionFacturacion(direccion);
		checkout.setMetodoEnvio(metodo);
		checkout.setDescuento(descuento);
		checkout.setMoneda("PEN");
		checkout.setSubtotal(new BigDecimal("100.00"));
		checkout.setDescuentoTotal(new BigDecimal("10.00"));
		checkout.setImpuestoTotal(BigDecimal.ZERO);
		checkout.setEnvioTotal(BigDecimal.ZERO);
		checkout.setTotal(new BigDecimal("90.00"));
		checkout.setEstado(CheckoutSesion.PENDIENTE);
		return checkouts.save(checkout).getId();
	}
}
