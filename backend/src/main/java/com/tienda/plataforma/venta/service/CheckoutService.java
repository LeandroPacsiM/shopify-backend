package com.tienda.plataforma.venta.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.tienda.plataforma.venta.entity.CheckoutDescuento;
import com.tienda.plataforma.venta.entity.CheckoutSesion;
import com.tienda.plataforma.venta.entity.Descuento;
import com.tienda.plataforma.venta.entity.MetodoEnvio;
import com.tienda.plataforma.venta.entity.TasaImpuesto;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;
import com.tienda.plataforma.venta.repository.CheckoutDescuentoRepository;
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
	private final CheckoutDescuentoRepository checkoutDescuentos;
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
			CheckoutDescuentoRepository checkoutDescuentos, FacturacionService facturacion) {
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
		this.checkoutDescuentos = checkoutDescuentos;
		this.facturacion = facturacion;
	}

	@Transactional
	public CheckoutResponse crear(Long clienteId, String slug, CrearCheckoutRequest req) {
		Tienda tienda = tiendas.findBySlug(slug)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		facturacion.exigirActiva(tienda.getId());
		Cliente cliente = clientes.findById(clienteId)
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		if (!cliente.getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("carrito");
		}
		Carrito carrito = carritos.findByToken(req.carritoToken())
				.orElseThrow(() -> new NoEncontradoException("carrito"));
		if (!carrito.getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("carrito");
		}
		if (!Carrito.ACTIVO.equals(carrito.getEstado())) {
			throw new ConflictoException("carrito no válido");
		}
		if (carrito.getCliente() == null) {
			carrito.setCliente(cliente);
			carritos.save(carrito);
		} else if (!carrito.getCliente().getId().equals(cliente.getId())
				|| !carrito.getCliente().getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("carrito");
		}
		List<CarritoItem> lineas = items.findByCarritoId(carrito.getId());
		if (lineas.isEmpty()) {
			throw new ConflictoException("carrito vacío");
		}
		for (CarritoItem l : lineas) {
			if (!l.getVariante().getProducto().getTienda().getId().equals(tienda.getId())) {
				throw new NoEncontradoException("carrito");
			}
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

		List<Descuento> descuentosAplicados = List.of();
		Map<Long, BigDecimal> montosPorDescuento = Map.of();
		BigDecimal montoDescuento = BigDecimal.ZERO;
		List<String> codigos = codigosCombinados(req);
		if (!codigos.isEmpty()) {
			var calculados = validarYCalcularTodos(tienda.getId(), codigos, lineas, subtotal,
					metodo.getPrecio());
			descuentosAplicados = calculados.descuentos();
			montosPorDescuento = calculados.montos();
			montoDescuento = calculados.total();
		}

		BigDecimal base = subtotal.subtract(montoDescuento);
		List<TasaImpuesto> tasasTienda = tasas.findByTiendaIdAndActivoTrue(tienda.getId());
		BigDecimal tasaPct = tasasTienda.isEmpty() ? BigDecimal.ZERO : tasasTienda.get(0).getPorcentaje();
		BigDecimal impuesto = base.multiply(tasaPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
		BigDecimal envioTotal = descuentosAplicados.stream()
				.anyMatch(d -> Descuento.ENVIO_GRATIS.equals(d.getTipo())) ? BigDecimal.ZERO
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
		for (var d : descuentosAplicados) {
			CheckoutDescuento fila = new CheckoutDescuento();
			fila.setCheckoutSesion(checkout);
			fila.setDescuento(d);
			fila.setMontoAplicado(montosPorDescuento.get(d.getId()));
			checkoutDescuentos.save(fila);
		}

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

	/** Códigos únicos del request (nuevo plural + legado singular). */
	private List<String> codigosCombinados(CrearCheckoutRequest req) {
		List<String> codigos = new ArrayList<>();
		if (req.codigoDescuento() != null && !req.codigoDescuento().isBlank()) {
			codigos.add(req.codigoDescuento().trim());
		}
		if (req.codigosDescuento() != null) {
			req.codigosDescuento().stream()
					.filter(c -> c != null && !c.isBlank())
					.map(String::trim)
					.forEach(codigos::add);
		}
		return List.copyOf(new java.util.LinkedHashSet<>(codigos));
	}

	/**
	 * RF-DISC-01: un código una vez; hasta 5 de producto (porcentaje/monto)
	 * más 1 de envío por pedido. Cada descuento se calcula sobre su base
	 * elegible y la suma se topa al subtotal.
	 */
	private CalculoDescuentos validarYCalcularTodos(Long tiendaId, List<String> codigos,
			List<CarritoItem> lineas, BigDecimal subtotal, BigDecimal precioEnvio) {
		List<Descuento> lista = new ArrayList<>();
		for (String codigo : codigos) {
			Descuento d = descuentos.findByCodigoAndTiendaId(codigo, tiendaId)
					.orElseThrow(() -> new NoEncontradoException("descuento"));
			if (lista.stream().anyMatch(x -> x.getId().equals(d.getId()))) {
				throw new ConflictoException("código repetido: " + codigo);
			}
			lista.add(d);
		}
		long deProducto = lista.stream()
				.filter(d -> !Descuento.ENVIO_GRATIS.equals(d.getTipo())).count();
		long deEnvio = lista.size() - deProducto;
		if (deProducto > 5 || deEnvio > 1) {
			throw new ConflictoException("combinación no permitida (máx. 5 de producto + 1 de envío)");
		}
		Map<Long, BigDecimal> montos = new LinkedHashMap<>();
		BigDecimal sumaProducto = BigDecimal.ZERO;
		for (Descuento d : lista) {
			if (Descuento.ENVIO_GRATIS.equals(d.getTipo())) {
				continue;
			}
			BigDecimal monto = validarYCalcular(d, lineas, subtotal, precioEnvio);
			BigDecimal margen = subtotal.subtract(sumaProducto);
			if (monto.compareTo(margen) > 0) {
				monto = margen.max(BigDecimal.ZERO);
			}
			montos.put(d.getId(), monto);
			sumaProducto = sumaProducto.add(monto);
		}
		for (Descuento d : lista) {
			if (Descuento.ENVIO_GRATIS.equals(d.getTipo())) {
				validarYCalcular(d, lineas, subtotal, precioEnvio);
				montos.put(d.getId(), BigDecimal.ZERO);
			}
		}
		return new CalculoDescuentos(List.copyOf(lista), Map.copyOf(montos), escala(sumaProducto));
	}

	private record CalculoDescuentos(List<Descuento> descuentos, Map<Long, BigDecimal> montos,
			BigDecimal total) {
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
