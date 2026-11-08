package com.tienda.plataforma.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.repository.ColeccionProductoRepository;
import com.tienda.plataforma.platform.facturacion.service.FacturacionService;
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
import com.tienda.plataforma.venta.entity.CheckoutSesion;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.entity.TasaImpuesto;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.DescuentoColeccionRepository;
import com.tienda.plataforma.venta.repository.DescuentoProductoRepository;
import com.tienda.plataforma.venta.repository.DescuentoRepository;
import com.tienda.plataforma.venta.repository.MetodoEnvioRepository;
import com.tienda.plataforma.venta.repository.TasaImpuestoRepository;

/**
 * RF-CART-02/03 y RF-DISC: crea el checkout congelando totales.
 * El descuento se valida aquí pero se registra al confirmar (V6 + usos atómicos).
 */
@Service
public class CheckoutService {

	private final CheckoutSesionRepository checkouts;
	private final CarritoRepository carritos;
	private final CarritoItemRepository items;
	private final ClienteRepository clientes;
	private final TiendaRepository tiendas;
	private final DireccionRepository direcciones;
	private final MetodoEnvioRepository metodos;
	private final DescuentoRepository descuentos;
	private final DescuentoProductoRepository descuentoProductos;
	private final DescuentoColeccionRepository descuentoColecciones;
	private final ColeccionProductoRepository coleccionProductos;
	private final TasaImpuestoRepository tasas;
	private final FacturacionService facturacion;

	public CheckoutService(CheckoutSesionRepository checkouts, CarritoRepository carritos,
			CarritoItemRepository items, ClienteRepository clientes, TiendaRepository tiendas,
			DireccionRepository direcciones, MetodoEnvioRepository metodos, DescuentoRepository descuentos,
			DescuentoProductoRepository descuentoProductos, DescuentoColeccionRepository descuentoColecciones,
			ColeccionProductoRepository coleccionProductos, TasaImpuestoRepository tasas,
			FacturacionService facturacion) {
		this.checkouts = checkouts;
		this.carritos = carritos;
		this.items = items;
		this.clientes = clientes;
		this.tiendas = tiendas;
		this.direcciones = direcciones;
		this.metodos = metodos;
		this.descuentos = descuentos;
		this.descuentoProductos = descuentoProductos;
		this.descuentoColecciones = descuentoColecciones;
		this.coleccionProductos = coleccionProductos;
		this.tasas = tasas;
		this.facturacion = facturacion;
	}

	@Transactional
	public CheckoutResponse crear(Long usuarioId, String slug, CrearCheckoutRequest req) {
		Tienda tienda = tiendas.findBySlug(slug)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		facturacion.exigirActiva(tienda.getId());
		Cliente cliente = clientes.findByUsuarioId(usuarioId)
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		if (!cliente.getTienda().getId().equals(tienda.getId())) {
			throw new ConflictoException("cliente de otra tienda");
		}
		Carrito carrito = carritos.findByToken(req.carritoToken())
				.orElseThrow(() -> new NoEncontradoException("carrito"));
		if (!carrito.getTienda().getId().equals(tienda.getId())
				|| !Carrito.ACTIVO.equals(carrito.getEstado())) {
			throw new ConflictoException("carrito no válido");
		}
		if (carrito.getCliente() == null) {
			carrito.setCliente(cliente);
			carritos.save(carrito);
		} else if (!carrito.getCliente().getId().equals(cliente.getId())) {
			throw new ConflictoException("carrito de otro cliente");
		}
		List<CarritoItem> lineas = items.findByCarritoId(carrito.getId());
		if (lineas.isEmpty()) {
			throw new ConflictoException("carrito vacío");
		}
		for (CarritoItem l : lineas) {
			if (!l.getVariante().comprable()) {
				throw new ConflictoException("variante no comprable: " + l.getVariante().getSku());
			}
		}

		Direccion envio = direcciones.findById(req.direccionEnvioId())
				.orElseThrow(() -> new NoEncontradoException("dirección"));
		Direccion factura = req.direccionFacturacionId() == null ? envio
				: direcciones.findById(req.direccionFacturacionId())
						.orElseThrow(() -> new NoEncontradoException("dirección"));
		if (!envio.getCliente().getId().equals(cliente.getId())
				|| !factura.getCliente().getId().equals(cliente.getId())) {
			throw new ConflictoException("dirección ajena");
		}
		MetodoEnvio metodo = metodos.findById(req.metodoEnvioId())
				.orElseThrow(() -> new NoEncontradoException("método de envío"));
		if (!metodo.getTienda().getId().equals(tienda.getId()) || !metodo.getActivo()) {
			throw new ConflictoException("método no válido");
		}

		BigDecimal subtotal = lineas.stream()
				.map(l -> l.getVariante().getPrecio().multiply(BigDecimal.valueOf(l.getCantidad())))
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		Descuento descuento = null;
		BigDecimal montoDescuento = BigDecimal.ZERO;
		if (req.codigoDescuento() != null && !req.codigoDescuento().isBlank()) {
			descuento = descuentos.findByCodigoAndTiendaId(req.codigoDescuento().trim(), tienda.getId())
					.orElseThrow(() -> new NoEncontradoException("descuento"));
			montoDescuento = validarYCalcular(descuento, lineas, subtotal, metodo.getPrecio());
		}

		BigDecimal base = subtotal.subtract(montoDescuento);
		List<TasaImpuesto> tasasTienda = tasas.findByTiendaIdAndActivoTrue(tienda.getId());
		BigDecimal tasaPct = tasasTienda.isEmpty() ? BigDecimal.ZERO : tasasTienda.get(0).getPorcentaje();
		BigDecimal impuesto = base.multiply(tasaPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		BigDecimal envioTotal = Descuento.ENVIO_GRATIS.equals(descuentoTipo(descuento)) ? BigDecimal.ZERO
				: metodo.getPrecio();

		CheckoutSesion checkout = new CheckoutSesion();
		checkout.setTienda(tienda);
		checkout.setCarrito(carrito);
		checkout.setCliente(cliente);
		checkout.setDireccionEnvio(envio);
		checkout.setDireccionFacturacion(factura);
		checkout.setMetodoEnvio(metodo);
		checkout.setEmailContacto(req.emailContacto());
		checkout.setTelefonoContacto(req.telefonoContacto());
		checkout.setNotasCliente(req.notasCliente());
		if (descuento != null) {
			checkout.setDescuento(descuento);
		}
		checkout.setMoneda(carrito.getMoneda());
		checkout.setSubtotal(escala(subtotal));
		checkout.setDescuentoTotal(escala(montoDescuento));
		checkout.setImpuestoTotal(escala(impuesto));
		checkout.setEnvioTotal(escala(envioTotal));
		checkout.setTotal(escala(base.add(impuesto).add(envioTotal)));
		checkout.setEstado(CheckoutSesion.PENDIENTE);
		checkout.setExpiraEn(Instant.now().plusSeconds(30 * 60));
		checkout.setCreadoEn(Instant.now());
		checkouts.save(checkout);

		// Actualiza precios del carrito a los vigentes (el pedido usará estos).
		for (CarritoItem l : lineas) {
			l.setPrecioUnitario(l.getVariante().getPrecio());
			items.save(l);
		}
		return new CheckoutResponse(checkout.getId(), checkout.getEstado(), checkout.getSubtotal(),
				checkout.getDescuentoTotal(), checkout.getImpuestoTotal(), checkout.getEnvioTotal(),
				checkout.getTotal(), checkout.getExpiraEn(), checkout.getEmailContacto(),
				checkout.getTelefonoContacto(), checkout.getNotasCliente());
	}

	private String descuentoTipo(Descuento descuento) {
		return descuento == null ? null : descuento.getTipo();
	}

	/** Valida vigencia/alcance y calcula el monto. Simplificación: primera tasa activa de la tienda. */
	BigDecimal validarYCalcular(Descuento descuento, List<CarritoItem> lineas, BigDecimal subtotal,
			BigDecimal precioEnvio) {
		if (!descuento.vigente(Instant.now())) {
			throw new ConflictoException("descuento no vigente");
		}
		var prodIds = descuentoProductos.findByDescuentoId(descuento.getId()).stream()
				.map(dp -> dp.getProducto().getId()).toList();
		var coleIds = descuentoColecciones.findByDescuentoId(descuento.getId()).stream()
				.map(dc -> dc.getColeccion().getId()).toList();
		BigDecimal elegible = subtotal;
		if (!prodIds.isEmpty() || !coleIds.isEmpty()) {
			elegible = lineas.stream()
					.filter(l -> prodIds.contains(l.getVariante().getProducto().getId())
							|| coleccionProductos.findByProductoId(l.getVariante().getProducto().getId()).stream()
									.anyMatch(cp -> coleIds.contains(cp.getColeccion().getId())))
					.map(l -> l.getVariante().getPrecio().multiply(BigDecimal.valueOf(l.getCantidad())))
					.reduce(BigDecimal.ZERO, BigDecimal::add);
			if (elegible.signum() <= 0) {
				throw new ConflictoException("descuento no aplica al carrito");
			}
		}
		return switch (descuento.getTipo()) {
			case Descuento.PORCENTAJE ->
				escala(elegible.multiply(descuento.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
			case Descuento.MONTO_FIJO -> escala(descuento.getValor().min(elegible));
			case Descuento.ENVIO_GRATIS -> escala(precioEnvio);
			default -> throw new ConflictoException("tipo de descuento desconocido");
		};
	}

	private BigDecimal escala(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}
}
