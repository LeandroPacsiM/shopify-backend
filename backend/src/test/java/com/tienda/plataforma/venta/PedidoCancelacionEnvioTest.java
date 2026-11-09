package com.tienda.plataforma.venta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.envios.dto.CrearEnvioRequest;
import com.tienda.plataforma.envios.dto.TrackingWriteRequest;
import com.tienda.plataforma.envios.repository.EnvioEventoRepository;
import com.tienda.plataforma.envios.repository.EnvioRepository;
import com.tienda.plataforma.envios.service.EnvioService;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.entity.MovimientoInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.entity.Ubicacion;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.inventario.repository.MovimientoInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.inventario.repository.UbicacionRepository;
import com.tienda.plataforma.pagos.entity.Pago;
import com.tienda.plataforma.pagos.entity.PasarelaPago;
import com.tienda.plataforma.pagos.repository.PagoRepository;
import com.tienda.plataforma.pagos.repository.PasarelaPagoRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoDescuento;
import com.tienda.plataforma.venta.entity.PedidoItem;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.PedidoDescuentoRepository;
import com.tienda.plataforma.venta.repository.PedidoItemRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;
import com.tienda.plataforma.venta.service.AdminPedidoService;

/** ORDER-01: cancelar sin perder cobros, despacho consume reserva, tracking ordenado. */
@SpringBootTest
@ActiveProfiles("test")
class PedidoCancelacionEnvioTest {

	@Autowired
	AdminPedidoService adminPedidos;
	@Autowired
	EnvioService enviosSvc;
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
	UbicacionRepository ubicaciones;
	@Autowired
	ArticuloInventarioRepository articulos;
	@Autowired
	NivelInventarioRepository niveles;
	@Autowired
	MovimientoInventarioRepository movimientos;
	@Autowired
	PedidoRepository pedidos;
	@Autowired
	PedidoItemRepository pedidoItems;
	@Autowired
	DescuentoRepository descuentos;
	@Autowired
	PedidoDescuentoRepository pedidoDescuentos;
	@Autowired
	PagoRepository pagos;
	@Autowired
	PasarelaPagoRepository pasarelas;
	@Autowired
	EnvioRepository envios;
	@Autowired
	EnvioEventoRepository envioEventos;

	@Test
	void cancelarPendienteLiberaReservaYRevierteDescuento() {
		Fixture f = fixture(Pedido.PENDIENTE, false);
		niveles.findById(f.nivelId()).ifPresent(n -> {
			assertEquals(2, n.getReservado());
		});
		assertEquals(1, descuentos.findById(f.descuentoId()).orElseThrow().getUsosActuales());

		var resp = adminPedidos.cancelar(f.tiendaId(), f.pedidoId());

		assertEquals(Pedido.CANCELADO, resp.estado());
		assertEquals(0, niveles.findById(f.nivelId()).orElseThrow().getReservado());
		assertEquals(0, descuentos.findById(f.descuentoId()).orElseThrow().getUsosActuales());
		assertTrue(movimientos.findByReferencia(f.numero()).stream()
				.anyMatch(m -> MovimientoInventario.LIBERACION.equals(m.getTipoMovimiento())));
	}

	@Test
	void cancelarPagadoConCobroSeRechaza() {
		Fixture f = fixture(Pedido.PAGADO, true);
		assertThrows(ConflictoException.class,
				() -> adminPedidos.cancelar(f.tiendaId(), f.pedidoId()));
		assertEquals(Pedido.PAGADO, pedidos.findById(f.pedidoId()).orElseThrow().getEstado());
	}

	@Test
	void crearEnvioExigePedidoPagado() {
		Fixture f = fixture(Pedido.PENDIENTE, false);
		CrearEnvioRequest req = new CrearEnvioRequest(null, null,
				List.of(new CrearEnvioRequest.ItemEnvio(f.itemId(), 1)));
		assertThrows(ConflictoException.class, () -> enviosSvc.crear(f.tiendaId(), f.pedidoId(), req));
	}

	@Test
	void crearEnvioConsumeReservaYPreparaPedido() {
		Fixture f = fixture(Pedido.PAGADO, false);
		CrearEnvioRequest req = new CrearEnvioRequest(null, null,
				List.of(new CrearEnvioRequest.ItemEnvio(f.itemId(), 2)));
		var envio = enviosSvc.crear(f.tiendaId(), f.pedidoId(), req);

		NivelInventario nivel = niveles.findById(f.nivelId()).orElseThrow();
		assertEquals(8, nivel.getDisponible());
		assertEquals(0, nivel.getReservado());
		assertTrue(movimientos.findByReferencia(f.numero()).stream()
				.anyMatch(m -> MovimientoInventario.SALIDA.equals(m.getTipoMovimiento())
						&& m.getCantidad() == 2));
		assertEquals(Pedido.EN_PREPARACION,
				pedidos.findById(f.pedidoId()).orElseThrow().getEstado());
		assertEquals(com.tienda.plataforma.envios.entity.Envio.PREPARANDO, envio.estado());
	}

	@Test
	void trackingAtrasadoNoRetrocedeYSubePedidoAEnviado() {
		Fixture f = fixture(Pedido.PAGADO, false);
		CrearEnvioRequest req = new CrearEnvioRequest(null, null,
				List.of(new CrearEnvioRequest.ItemEnvio(f.itemId(), 2)));
		var envio = enviosSvc.crear(f.tiendaId(), f.pedidoId(), req);
		Instant base = Instant.now();

		enviosSvc.tracking(new TrackingWriteRequest(envio.numeroTracking(), "enviado",
				null, null, base, "order01-e1"));
		assertEquals(com.tienda.plataforma.envios.entity.Envio.ENVIADO,
				envios.findById(envio.id()).orElseThrow().getEstado());
		assertEquals(Pedido.ENVIADO, pedidos.findById(f.pedidoId()).orElseThrow().getEstado());

		enviosSvc.tracking(new TrackingWriteRequest(envio.numeroTracking(), "preparando",
				null, null, base.minusSeconds(3600), "order01-e2"));
		assertEquals(com.tienda.plataforma.envios.entity.Envio.ENVIADO,
				envios.findById(envio.id()).orElseThrow().getEstado());
		assertEquals(2, envioEventos.findByEnvioIdOrderByOcurridoEnAsc(envio.id()).size());
	}

	private record Fixture(Long tiendaId, Long pedidoId, Long itemId, Long nivelId,
			Long descuentoId, String numero) {
	}

	private Fixture fixture(String estadoPedido, boolean conPago) {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		Tienda tienda = new Tienda();
		tienda.setNombre("OC-" + tag);
		tienda.setSlug("oc-" + tag);
		tienda.setActiva(true);
		tiendas.save(tienda);

		Usuario usuario = new Usuario();
		usuario.setEmail("oc-" + tag + "@example.com");
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
		clientes.save(cliente);

		Producto producto = new Producto();
		producto.setTienda(tienda);
		producto.setTitulo("OC-" + tag);
		producto.setSlug("ocp-" + tag);
		producto.setEstado(Producto.ACTIVO);
		productos.save(producto);
		ProductoVariante variante = new ProductoVariante();
		variante.setProducto(producto);
		variante.setSku("OSKU-" + tag);
		variante.setPrecio(new BigDecimal("50.00"));
		variante.setActivo(true);
		variantes.save(variante);

		Ubicacion ubicacion = new Ubicacion();
		ubicacion.setTienda(tienda);
		ubicacion.setNombre("OU-" + tag);
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
		nivel.setReservado(2);
		niveles.save(nivel);
		MovimientoInventario reserva = new MovimientoInventario();
		reserva.setNivel(nivel);
		reserva.setTipoMovimiento(MovimientoInventario.RESERVA);
		reserva.setCantidad(2);
		reserva.setMotivo("Reserva del pedido OC-" + tag);
		String numero = "OC-" + tag;
		reserva.setReferencia(numero);
		reserva.setCreadoEn(Instant.now());
		movimientos.save(reserva);

		Descuento descuento = new Descuento();
		descuento.setTienda(tienda);
		descuento.setCodigo("OCUPON-" + tag);
		descuento.setTipo(Descuento.PORCENTAJE);
		descuento.setValor(new BigDecimal("10.00"));
		descuento.setUsosActuales(1);
		descuento.setActivo(true);
		descuentos.save(descuento);

		Pedido pedido = new Pedido();
		pedido.setTienda(tienda);
		pedido.setCliente(cliente);
		pedido.setNumeroPedido(numero);
		pedido.setEstado(estadoPedido);
		pedido.setMoneda("PEN");
		pedido.setSubtotal(new BigDecimal("100.00"));
		pedido.setImpuestoTotal(BigDecimal.ZERO);
		pedido.setEnvioTotal(BigDecimal.ZERO);
		pedido.setDescuentoTotal(BigDecimal.ZERO);
		pedido.setTotal(new BigDecimal("100.00"));
		pedidos.save(pedido);

		PedidoItem item = new PedidoItem();
		item.setPedido(pedido);
		item.setVariante(variante);
		item.setProductoTitulo(producto.getTitulo());
		item.setVarianteTitulo(variante.getSku());
		item.setSku(variante.getSku());
		item.setCantidad(2);
		item.setPrecioUnitario(new BigDecimal("50.00"));
		item.setPrecioTotal(new BigDecimal("100.00"));
		pedidoItems.save(item);

		PedidoDescuento aplicacion = new PedidoDescuento();
		aplicacion.setPedido(pedido);
		aplicacion.setDescuento(descuento);
		aplicacion.setMontoAplicado(BigDecimal.ZERO);
		pedidoDescuentos.save(aplicacion);

		if (conPago) {
			PasarelaPago pasarela = pasarelas.findByCodigo("mock").orElseGet(() -> {
				PasarelaPago p = new PasarelaPago();
				p.setNombre("Mock");
				p.setCodigo("mock");
				p.setActivo(true);
				return pasarelas.save(p);
			});
			Pago pago = new Pago();
			pago.setPedido(pedido);
			pago.setPasarela(pasarela);
			pago.setEstado(Pago.APROBADO);
			pago.setMonto(new BigDecimal("100.00"));
			pago.setMoneda("PEN");
			pago.setTransaccionExternaId("mock-paid-" + tag);
			pagos.save(pago);
		}
		return new Fixture(tienda.getId(), pedido.getId(), item.getId(), nivel.getId(),
				descuento.getId(), numero);
	}
}
