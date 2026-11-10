package com.tienda.plataforma.tenants;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.ClienteSesion;
import com.tienda.plataforma.auth.entity.CodigoCliente;
import com.tienda.plataforma.auth.entity.Sesion;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.ClienteSesionRepository;
import com.tienda.plataforma.auth.repository.CodigoClienteRepository;
import com.tienda.plataforma.auth.repository.SesionRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.security.JwtService;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CheckoutSesion;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoDireccion;
import com.tienda.plataforma.venta.entity.Resena;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.PedidoDireccionRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;
import com.tienda.plataforma.venta.repository.ResenaRepository;

/** GDPR: anonimiza PII y conserva historial. Rollback al final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrivacidadTest {

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
	DireccionRepository direcciones;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PedidoDireccionRepository pedidoDirecciones;
	@Autowired
	ResenaRepository resenas;
	@Autowired
	SesionRepository sesiones;
	@Autowired
	ClienteSesionRepository clienteSesiones;
	@Autowired
	CodigoClienteRepository clienteCodigos;
	@Autowired
	CarritoRepository carritos;
	@Autowired
	CheckoutSesionRepository checkouts;
	@Autowired
	ProductoRepository productos;

	Tienda tienda;
	Usuario usuario;
	Cliente cliente;
	Pedido pedido;
	CheckoutSesion checkout;
	String jwt;
	String email;

	@BeforeEach
	void fixtures() {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		tienda = new Tienda();
		tienda.setNombre("GD-" + tag);
		tienda.setSlug("gd-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		usuario = new Usuario();
		email = "gd-" + tag + "@example.com";
		usuario.setEmail(email);
		usuario.setPasswordHash("x");
		usuario.setNombre("Nombre");
		usuario.setApellido("Apellido");
		usuario.setActivo(true);
		usuario.setEmailVerificado(true);
		usuario.setIntentosFallidos(0);
		usuarios.save(usuario);

		cliente = new Cliente();
		cliente.setTienda(tienda);
		cliente.setNombre("Nombre");
		cliente.setApellido("Apellido");
		cliente.setTelefono("999");
		cliente.setAceptaMarketing(false);
		cliente.setEmail(usuario.getEmail());
		clientes.save(cliente);

		Direccion d = new Direccion();
		d.setCliente(cliente);
		d.setLinea1("Calle 1");
		d.setCiudad("Lima");
		d.setPais("PE");
		direcciones.save(d);

		pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido("GD-" + tag);
		pedido.setEstado(Pedido.PAGADO);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(BigDecimal.TEN);
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(BigDecimal.TEN);
		pedido.setEmailContacto("gd-" + tag + "@example.com");
		pedido.setTelefonoContacto("999");
		pedido.setNotasCliente("nota");
		pedidos.save(pedido);

		PedidoDireccion pd = new PedidoDireccion();
		pd.setPedido(pedido);
		pd.setTipo(PedidoDireccion.ENVIO);
		pd.setNombreDestinatario("Nombre Apellido");
		pd.setLinea1("Calle 1");
		pd.setCiudad("Lima");
		pd.setPais("PE");
		pedidoDirecciones.save(pd);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("GD-" + tag);
		producto.setSlug("gdp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);

		Resena r = new Resena();
		r.setProducto(producto);
		r.setCliente(cliente);
		r.setCalificacion(5);
		r.setTitulo("Título");
		r.setComentario("Comentario");
		r.setAprobada(true);
		resenas.save(r);

		Sesion s = new Sesion();
		s.setUsuario(usuario);
		s.setTokenHash("gdpr-" + tag);
		s.setFamiliaId(UUID.randomUUID());
		s.setExpiraEn(Instant.now().plusSeconds(30 * 24 * 60 * 60));
		sesiones.save(s);

		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		carrito.setCliente(cliente);
		carrito.setToken("gd-cart-" + tag);
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carritos.save(carrito);

		checkout = new CheckoutSesion();
		checkout.setTienda(tienda);
		checkout.setCarrito(carrito);
		checkout.setCliente(cliente);
		checkout.setMoneda("PEN");
		checkout.setSubtotal(BigDecimal.TEN);
		checkout.setImpuestoTotal(BigDecimal.ZERO);
		checkout.setEnvioTotal(BigDecimal.ZERO);
		checkout.setDescuentoTotal(BigDecimal.ZERO);
		checkout.setTotal(BigDecimal.TEN);
		checkout.setEstado(CheckoutSesion.COMPLETADO);
		checkout.setEmailContacto("gd-" + tag + "@example.com");
		checkout.setTelefonoContacto("999");
		checkout.setNotasCliente("nota checkout");
		checkouts.save(checkout);

		jwt = jwtService.accesoCliente(cliente.getId(), tienda.getId());

		ClienteSesion cs = new ClienteSesion();
		cs.setCliente(cliente);
		cs.setTokenHash("gdpr-c-" + tag);
		cs.setFamiliaId(UUID.randomUUID());
		cs.setExpiraEn(Instant.now().plusSeconds(7 * 24 * 60 * 60));
		clienteSesiones.save(cs);

		CodigoCliente cc = new CodigoCliente();
		cc.setTienda(tienda);
		cc.setEmail(cliente.getEmail());
		cc.setCliente(cliente);
		cc.setCodigoHmac("gdpr-h-" + tag);
		cc.setExpiraEn(Instant.now().plusSeconds(600));
		clienteCodigos.save(cc);
	}

	@Test
	void anonimizaSinBorrar() throws Exception {
		mvc.perform(delete("/api/storefront/{slug}/cuenta", tienda.getSlug())
				.header("Authorization", "Bearer " + jwt)
				.header("X-Tienda-Slug", tienda.getSlug()))
				.andExpect(status().isNoContent());

		Usuario u = usuarios.findById(usuario.getId()).orElseThrow();
		assertEquals(email, u.getEmail());
		assertTrue(u.getActivo());
		assertTrue(sesiones.findByUsuarioId(usuario.getId()).stream()
				.allMatch(s -> s.getRevocadoEn() == null));

		Cliente c = clientes.findById(cliente.getId()).orElseThrow();
		assertNull(c.getTelefono());
		assertEquals("eliminado-" + cliente.getId() + "@anonimo.local", c.getEmail());
		assertTrue(clienteSesiones.findByClienteId(cliente.getId()).stream()
				.allMatch(s -> s.getRevocadoEn() != null));
		assertTrue(clienteCodigos
				.findByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
						tienda.getId(), email)
				.isEmpty());

		assertTrue(direcciones.findByClienteId(cliente.getId()).stream()
				.allMatch(d -> "Eliminada".equals(d.getLinea1()) && d.getCiudad() == null));

		Pedido p = pedidos.findById(pedido.getId()).orElseThrow();
		assertNull(p.getEmailContacto());
		assertNull(p.getTelefonoContacto());
		assertEquals(0, p.getTotal().compareTo(new BigDecimal("10.00")));

		CheckoutSesion sesion = checkouts.findById(checkout.getId()).orElseThrow();
		assertNull(sesion.getEmailContacto());
		assertNull(sesion.getTelefonoContacto());
		assertNull(sesion.getNotasCliente());
		assertEquals(0, sesion.getTotal().compareTo(new BigDecimal("10.00")));

		assertTrue(pedidoDirecciones.findByPedidoId(pedido.getId()).stream()
				.allMatch(d -> "Eliminado".equals(d.getNombreDestinatario())));

		assertTrue(resenas.findByClienteId(cliente.getId()).stream()
				.allMatch(r -> r.getTitulo() == null && r.getComentario() == null
						&& r.getCalificacion() == 5));
	}
}
