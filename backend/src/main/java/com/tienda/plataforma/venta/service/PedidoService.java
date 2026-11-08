package com.tienda.plataforma.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.inventario.entity.ArticuloInventario;
import com.tienda.plataforma.inventario.entity.MovimientoInventario;
import com.tienda.plataforma.inventario.entity.NivelInventario;
import com.tienda.plataforma.inventario.repository.ArticuloInventarioRepository;
import com.tienda.plataforma.inventario.repository.MovimientoInventarioRepository;
import com.tienda.plataforma.inventario.repository.NivelInventarioRepository;
import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.venta.dto.CheckoutResponse;
import com.tienda.plataforma.venta.dto.CrearCheckoutRequest;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CarritoItem;
import com.tienda.plataforma.venta.entity.CheckoutSesion;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.HistorialEstadoPedido;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.entity.PedidoDescuento;
import com.tienda.plataforma.venta.entity.PedidoDireccion;
import com.tienda.plataforma.venta.entity.PedidoImpuesto;
import com.tienda.plataforma.venta.entity.PedidoItem;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.HistorialEstadoPedidoRepository;
import com.tienda.plataforma.venta.repository.PedidoDescuentoRepository;
import com.tienda.plataforma.venta.repository.PedidoDireccionRepository;
import com.tienda.plataforma.venta.repository.PedidoImpuestoRepository;
import com.tienda.plataforma.venta.repository.PedidoItemRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;

/**
 * RF-CART-03 y RF-ORDER: confirmación transaccional con locks, snapshots,
 * reserva de stock e historial. Doble confirmación concurrente crea un solo pedido.
 */
@Service
public class PedidoService {

	private final CheckoutService checkoutService;
	private final CheckoutSesionRepository checkouts;
	private final CarritoRepository carritos;
	private final CarritoItemRepository items;
	private final PedidoRepository pedidos;
	private final PedidoItemRepository pedidoItems;
	private final PedidoDireccionRepository pedidoDirecciones;
	private final PedidoImpuestoRepository pedidoImpuestos;
	private final PedidoDescuentoRepository pedidoDescuentos;
	private final HistorialEstadoPedidoRepository historial;
	private final DescuentoRepository descuentos;
	private final ArticuloInventarioRepository articulos;
	private final NivelInventarioRepository niveles;
	private final MovimientoInventarioRepository movimientos;
	private final NotificacionService notificaciones;
	private final SecureRandom aleatorio = new SecureRandom();

	public PedidoService(CheckoutService checkoutService, CheckoutSesionRepository checkouts,
			CarritoRepository carritos, CarritoItemRepository items, PedidoRepository pedidos,
			PedidoItemRepository pedidoItems, PedidoDireccionRepository pedidoDirecciones,
			PedidoImpuestoRepository pedidoImpuestos, PedidoDescuentoRepository pedidoDescuentos,
			HistorialEstadoPedidoRepository historial, DescuentoRepository descuentos,
			ArticuloInventarioRepository articulos, NivelInventarioRepository niveles,
			MovimientoInventarioRepository movimientos, NotificacionService notificaciones) {
		this.checkoutService = checkoutService;
		this.checkouts = checkouts;
		this.carritos = carritos;
		this.items = items;
		this.pedidos = pedidos;
		this.pedidoItems = pedidoItems;
		this.pedidoDirecciones = pedidoDirecciones;
		this.pedidoImpuestos = pedidoImpuestos;
		this.pedidoDescuentos = pedidoDescuentos;
		this.historial = historial;
		this.descuentos = descuentos;
		this.articulos = articulos;
		this.niveles = niveles;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
	}

	public CheckoutResponse crearCheckout(Long usuarioId, String slug, CrearCheckoutRequest req) {
		return checkoutService.crear(usuarioId, slug, req);
	}

	@Transactional
	public PedidoResponse confirmar(Long usuarioId, Long checkoutId) {
		CheckoutSesion checkout = checkouts.findConBloqueoById(checkoutId)
				.orElseThrow(() -> new NoEncontradoException("checkout"));
		if (!checkout.getCliente().getUsuario().getId().equals(usuarioId)) {
			throw new NoEncontradoException("checkout");
		}
		if (!CheckoutSesion.PENDIENTE.equals(checkout.getEstado())) {
			throw new ConflictoException("checkout no pendiente");
		}
		if (checkout.getExpiraEn() != null && checkout.getExpiraEn().isBefore(Instant.now())) {
			checkout.setEstado(CheckoutSesion.EXPIRADO);
			checkouts.save(checkout);
			throw new ConflictoException("checkout expirado");
		}
		if (pedidos.findByCheckoutSesionId(checkoutId).isPresent()) {
			throw new ConflictoException("checkout ya convertido");
		}
		List<CarritoItem> lineas = items.findByCarritoId(checkout.getCarrito().getId());
		if (lineas.isEmpty()) {
			throw new ConflictoException("carrito vacío");
		}
		for (CarritoItem l : lineas) {
			if (!l.getVariante().comprable()
					|| l.getVariante().getPrecio().compareTo(l.getPrecioUnitario()) != 0) {
				throw new ConflictoException("precios cambiaron, reinicia el checkout");
			}
		}

		Pedido pedido = new Pedido();
		pedido.setTienda(checkout.getTienda());
		pedido.setCliente(checkout.getCliente());
		pedido.setCheckoutSesion(checkout);
		pedido.setNumeroPedido(generarNumero(checkout.getTienda().getId()));
		pedido.setEstado(Pedido.PENDIENTE);
		pedido.setMoneda(checkout.getMoneda());
		pedido.setSubtotal(checkout.getSubtotal());
		pedido.setDescuentoTotal(checkout.getDescuentoTotal());
		pedido.setImpuestoTotal(checkout.getImpuestoTotal());
		pedido.setEnvioTotal(checkout.getEnvioTotal());
		pedido.setTotal(checkout.getTotal());
		pedido.setEmailContacto(checkout.getEmailContacto());
		pedido.setTelefonoContacto(checkout.getTelefonoContacto());
		pedido.setNotasCliente(checkout.getNotasCliente());
		pedido.setRealizadoEn(Instant.now());
		pedido.setCreadoEn(Instant.now());
		pedido.setActualizadoEn(Instant.now());
		pedidos.save(pedido);

		if (checkout.getDescuento() != null) {
			consumirDescuento(pedido, checkout);
		}

		for (CarritoItem l : lineas) {
			PedidoItem item = new PedidoItem();			item.setPedido(pedido);
			item.setVariante(l.getVariante());
			item.setProductoTitulo(l.getVariante().getProducto().getTitulo());
			item.setVarianteTitulo(l.getVariante().getSku());
			item.setSku(l.getVariante().getSku());
			item.setCantidad(l.getCantidad());
			item.setPrecioUnitario(l.getPrecioUnitario());
			item.setPrecioTotal(l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad())));
			pedidoItems.save(item);
			reservarStock(pedido, l);
		}

		copiarDireccion(pedido, checkout.getDireccionEnvio(), PedidoDireccion.ENVIO);
		if (!checkout.getDireccionFacturacion().getId()
				.equals(checkout.getDireccionEnvio().getId())) {
			copiarDireccion(pedido, checkout.getDireccionFacturacion(), PedidoDireccion.FACTURACION);
		}

		PedidoImpuesto impuesto = new PedidoImpuesto();
		impuesto.setPedido(pedido);
		impuesto.setPedidoItem(null);
		impuesto.setTitulo("Impuesto checkout");
		impuesto.setTasa(BigDecimal.ZERO);
		impuesto.setMonto(checkout.getImpuestoTotal());
		impuesto.setCreadoEn(Instant.now());
		pedidoImpuestos.save(impuesto);

		checkout.setEstado(CheckoutSesion.COMPLETADO);
		checkouts.save(checkout);
		Carrito carrito = checkout.getCarrito();
		carrito.setEstado(Carrito.CONVERTIDO);
		carrito.setActualizadoEn(Instant.now());
		carritos.save(carrito);

		HistorialEstadoPedido h = new HistorialEstadoPedido();
		h.setPedido(pedido);
		h.setEstadoAnterior(null);
		h.setEstadoNuevo(Pedido.PENDIENTE);
		h.setComentario("Pedido creado desde checkout " + checkout.getId());
		h.setCreadoEn(Instant.now());
		historial.save(h);

		notificaciones.notificar(checkout.getTienda().getId(), NotificacionService.NUEVO_PEDIDO,
				Map.of("pedidoId", pedido.getId(), "numero", pedido.getNumeroPedido(), "total",
						pedido.getTotal().toString()));
		return new PedidoResponse(pedido.getId(), pedido.getNumeroPedido(), pedido.getEstado(), pedido.getTotal(),
				pedido.getEmailContacto(), pedido.getTelefonoContacto(), pedido.getNotasCliente());
	}

	/** Revalida con bloqueo, registra la aplicación y consume un uso (RF-DISC-02). */
	private void consumirDescuento(Pedido pedido, CheckoutSesion checkout) {
		Descuento descuento = descuentos.findConBloqueoById(checkout.getDescuento().getId())
				.orElseThrow(() -> new NoEncontradoException("descuento"));
		if (!descuento.vigente(Instant.now())) {
			throw new ConflictoException("descuento vencido");
		}
		if (descuento.getUsoMaximo() != null && descuento.getUsosActuales() >= descuento.getUsoMaximo()) {
			throw new ConflictoException("descuento agotado");
		}
		PedidoDescuento aplicacion = new PedidoDescuento();
		aplicacion.setPedido(pedido);
		aplicacion.setDescuento(descuento);
		aplicacion.setMontoAplicado(checkout.getDescuentoTotal());
		pedidoDescuentos.save(aplicacion);
		descuento.setUsosActuales(descuento.getUsosActuales() + 1);
		descuentos.save(descuento);
	}

	/** Reserva con bloqueo por nivel; falla si no hay vendible (controlaStock). */
	private void reservarStock(Pedido pedido, CarritoItem linea) {
		ArticuloInventario articulo = articulos.findByVarianteId(linea.getVariante().getId()).orElse(null);
		if (articulo == null || !Boolean.TRUE.equals(articulo.getControlaStock())) {
			return;
		}
		int pendiente = linea.getCantidad();
		List<NivelInventario> candidatos = niveles.findByArticuloId(articulo.getId());
		for (NivelInventario n : candidatos) {
			if (pendiente <= 0) {
				break;
			}
			int toma = Math.min(pendiente, n.vendible());
			if (toma <= 0) {
				continue;
			}
			if (niveles.reservarSiHayStock(n.getId(), toma) == 0) {
				continue;
			}
			NivelInventario nivel = niveles.findById(n.getId())
					.orElseThrow(() -> new NoEncontradoException("nivel"));
			MovimientoInventario mov = new MovimientoInventario();
			mov.setNivel(nivel);
			mov.setTipoMovimiento(MovimientoInventario.RESERVA);
			mov.setCantidad(toma);
			mov.setMotivo("Reserva del pedido " + pedido.getNumeroPedido());
			mov.setReferencia(pedido.getNumeroPedido());
			mov.setCreadoEn(Instant.now());
			movimientos.save(mov);
			pendiente -= toma;
		}
		if (pendiente > 0) {
			throw new ConflictoException("sin stock para " + linea.getVariante().getSku());
		}
	}

	private void copiarDireccion(Pedido pedido, com.tienda.plataforma.tenants.entity.Direccion origen, String tipo) {
		PedidoDireccion d = new PedidoDireccion();
		d.setPedido(pedido);
		d.setTipo(tipo);
		d.setLinea1(origen.getLinea1());
		d.setLinea2(origen.getLinea2());
		d.setCiudad(origen.getCiudad());
		d.setDepartamento(origen.getDepartamento());
		d.setCodigoPostal(origen.getCodigoPostal());
		d.setPais(origen.getPais());
		pedidoDirecciones.save(d);
	}

	private String generarNumero(Long tiendaId) {
		for (int i = 0; i < 3; i++) {
			String numero = "T" + tiendaId + "-" + Instant.now().getEpochSecond() + "-"
					+ (1000 + aleatorio.nextInt(9000));
			if (pedidos.findByNumeroPedido(numero).isEmpty()) {
				return numero;
			}
		}
		throw new ConflictoException("reintenta la confirmación");
	}

	/** Suma con redondeo uniforme para verificaciones. */
	static BigDecimal escala(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}
}
