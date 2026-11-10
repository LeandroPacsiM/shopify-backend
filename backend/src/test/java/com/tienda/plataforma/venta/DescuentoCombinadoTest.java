package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.dto.CheckoutResponse;
import com.tienda.plataforma.venta.dto.CrearCheckoutRequest;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CarritoItem;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.MetodoEnvioRepository;
import com.tienda.plataforma.venta.repository.PedidoDescuentoRepository;
import com.tienda.plataforma.venta.service.CheckoutService;
import com.tienda.plataforma.venta.service.PedidoService;

/** RF-DISC-01: varios códigos combinan con topes; el uso se consume por código. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DescuentoCombinadoTest {

	@Autowired
	CheckoutService checkouts;
	@Autowired
	PedidoService pedidos;
	@Autowired
	TiendaRepository tiendas;
	@Autowired
	UsuarioRepository usuarios;
	@Autowired
	ClienteRepository clientes;
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
	DescuentoRepository descuentos;
	@Autowired
	CarritoRepository carritos;
	@Autowired
	CarritoItemRepository carritoItems;
	@Autowired
	SuscripcionRepository suscripciones;
	@Autowired
	PedidoDescuentoRepository pedidoDescuentos;

	Tienda tienda;
	Usuario usuario;
	Cliente cliente;
	Direccion direccion;
	MetodoEnvio metodo;
	String d10;
	String d5;
	String env;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = nuevaTienda(tag);
		Suscripcion s = new Suscripcion();
		s.setTienda(tienda);
		s.setPlan(Suscripcion.MENSUAL);
		s.setEstado(Suscripcion.ACTIVA);
		s.setPeriodoFin(java.time.Instant.now().plusSeconds(30L * 24 * 60 * 60));
		suscripciones.save(s);

		usuario = new Usuario();
		usuario.setEmail("d-" + tag + "@example.com");
		usuario.setPasswordHash("x");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setTotpHabilitado(false);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		cliente = new Cliente();
		cliente.setTienda(tienda);
		cliente.setAceptaMarketing(false);
		cliente.setEmail(usuario.getEmail());
		clientes.save(cliente);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("D-" + tag);
		producto.setSlug("dp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("DSKU-" + tag);
		variante.setPrecio(new BigDecimal("100.00"));
		variante.setActivo(true);
		variantes.save(variante);
		ArticuloInventario articulo = new ArticuloInventario();
		articulo.setVariante(variante);
		articulo.setRequiereEnvio(false);
		articulo.setControlaStock(false);
		articulos.save(articulo);

		direccion = new Direccion();
		direccion.setCliente(cliente);
		direccion.setLinea1("Calle 1");
		direccion.setCiudad("Lima");
		direccion.setPais("PE");
		direcciones.save(direccion);

		metodo = new MetodoEnvio();
		metodo.setTienda(tienda);
		metodo.setNombre("Estándar");
		metodo.setPrecio(new BigDecimal("10.00"));
		metodo.setActivo(true);
		metodos.save(metodo);

		d10 = nuevoDescuento("D10-" + tag, Descuento.PORCENTAJE, "10.00");
		d5 = nuevoDescuento("D5-" + tag, Descuento.MONTO_FIJO, "5.00");
		env = nuevoDescuento("ENV-" + tag, Descuento.ENVIO_GRATIS, "0.00");
	}

	@Test
	void tresCodigosCombinanYConsumenUnUsoCadaUno() {
		CheckoutResponse checkout = crear(List.of(d10, d5, env), 2);

		assertEquals(new BigDecimal("200.00"), checkout.subtotal());
		assertEquals(new BigDecimal("25.00"), checkout.descuentoTotal());
		assertEquals(BigDecimal.ZERO.setScale(2), checkout.envioTotal());
		assertEquals(new BigDecimal("175.00"), checkout.total());

		var pedido = pedidos.confirmar(cliente.getId(), tienda.getId(), checkout.id());
		assertEquals(checkout.total(), pedido.total());
		assertEquals(new BigDecimal("25.00"), pedidoDescuentoTotal(pedido.id()));
		assertEquals(3, pedidoDescuentos.findAll().stream()
				.filter(pd -> pd.getPedido().getId().equals(pedido.id())).count());
		assertEquals(Integer.valueOf(1), usosDe(d10));
		assertEquals(Integer.valueOf(1), usosDe(d5));
		assertEquals(Integer.valueOf(1), usosDe(env));
	}

	@Test
	void dosEnviosGratisSeRechazan() {
		String env2 = nuevoDescuento("ENV2-" + UUID.randomUUID().toString().substring(0, 8),
				Descuento.ENVIO_GRATIS, "0.00");
		assertThrows(ConflictoException.class, () -> crear(List.of(env, env2), 1));
	}

	@Test
	void masDeCincoDeProductoSeRechazan() {
		List<String> seis = new ArrayList<>();
		seis.add(d10);
		seis.add(d5);
		for (int i = 0; i < 4; i++) {
			seis.add(nuevoDescuento("PX" + i + "-" + UUID.randomUUID().toString().substring(0, 8),
					Descuento.MONTO_FIJO, "1.00"));
		}
		assertThrows(ConflictoException.class, () -> crear(seis, 1));
	}

	@Test
	void codigoRepetidoSeAplicaUnaSolaVezYDesconocidoSeRechaza() {
		CheckoutResponse checkout = crear(List.of(d10, d10), 1);
		assertEquals(new BigDecimal("10.00"), checkout.descuentoTotal());
		assertThrows(NoEncontradoException.class, () -> crear(List.of("NO-EXISTE"), 1));
	}

	private CheckoutResponse crear(List<String> codigos, int cantidad) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("dtok-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);
		ProductoVariante variante = variantes.findAll().stream()
				.filter(v -> v.getProducto().getTienda().getId().equals(tienda.getId()))
				.findFirst().orElseThrow();
		CarritoItem item = new CarritoItem();
		item.setCarrito(carrito);
		item.setVariante(variante);
		item.setCantidad(cantidad);
		item.setPrecioUnitario(variante.getPrecio());
		carritoItems.save(item);
		var req = new CrearCheckoutRequest(carrito.getToken(), direccion.getId(), direccion.getId(),
				metodo.getId(), null, codigos, null, null, null);
		return checkouts.crear(cliente.getId(), tienda.getSlug(), req);
	}

	private BigDecimal pedidoDescuentoTotal(Long pedidoId) {
		return pedidoDescuentos.findAll().stream()
				.filter(pd -> pd.getPedido().getId().equals(pedidoId))
				.map(pd -> pd.getMontoAplicado())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private Integer usosDe(String codigo) {
		return descuentos.findByCodigoAndTiendaId(codigo, tienda.getId()).orElseThrow()
				.getUsosActuales();
	}

	private Tienda nuevaTienda(String tag) {
		Tienda t = new Tienda();
		t.setNombre("D-" + tag);
		t.setSlug("d-" + tag);
		t.setActiva(true);
		return tiendas.save(t);
	}

	private String nuevoDescuento(String codigo, String tipo, String valor) {
		Descuento d = new Descuento();
		d.setTienda(tienda);
		d.setCodigo(codigo);
		d.setTipo(tipo);
		d.setValor(new BigDecimal(valor));
		d.setUsosActuales(0);
		d.setActivo(true);
		descuentos.save(d);
		return codigo;
	}
}
