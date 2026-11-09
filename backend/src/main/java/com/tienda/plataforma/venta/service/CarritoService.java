package com.tienda.plataforma.venta.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.catalogo.entity.ProductoVariante;
import com.tienda.plataforma.catalogo.repository.ProductoVarianteRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.venta.dto.AgregarLineaRequest;
import com.tienda.plataforma.venta.dto.CarritoResponse;
import com.tienda.plataforma.venta.dto.CarritoResponse.LineaResponse;
import com.tienda.plataforma.venta.entity.Carrito;
import com.tienda.plataforma.venta.entity.CarritoItem;
import com.tienda.plataforma.venta.repository.CarritoItemRepository;
import com.tienda.plataforma.venta.repository.CarritoRepository;

/** RF-CART-01: carrito anónimo por token, una línea por variante, precio vigente. */
@Service
public class CarritoService {

	private final CarritoRepository carritos;
	private final CarritoItemRepository items;
	private final ProductoVarianteRepository variantes;
	private final ClienteRepository clientes;
	private final TiendaRepository tiendas;
	private final SecureRandom aleatorio = new SecureRandom();

	public CarritoService(CarritoRepository carritos, CarritoItemRepository items,
			ProductoVarianteRepository variantes, ClienteRepository clientes, TiendaRepository tiendas) {
		this.carritos = carritos;
		this.items = items;
		this.variantes = variantes;
		this.clientes = clientes;
		this.tiendas = tiendas;
	}

	/** Recupera por token o crea uno nuevo (anónimo o del cliente). */
	@Transactional
	public CarritoResponse obtenerOCrear(String slug, Long clienteId, String token) {
		Tienda tienda = tiendas.findBySlug(slug)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		Cliente cliente = clienteId == null ? null : clientes.findById(clienteId)
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		if (cliente != null && !cliente.getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("carrito");
		}

		Carrito carrito = null;
		if (token != null) {
			carrito = carritos.findByToken(token).orElse(null);
		}
		if (carrito != null) {
			validarTenantYTitular(carrito, tienda, cliente);
		}
		if (carrito == null && cliente != null) {
			carrito = carritos.findByClienteId(cliente.getId()).stream()
					.filter(c -> c.getTienda().getId().equals(tienda.getId())
							&& Carrito.ACTIVO.equals(c.getEstado()))
					.findFirst().orElse(null);
		}
		if (carrito == null) {
			carrito = nuevoCarrito(tienda, cliente);
		} else if (cliente != null && carrito.getCliente() == null) {
			carrito.setCliente(cliente);
			carritos.save(carrito);
		}
		return ver(carrito.getId());
	}

	@Transactional
	public CarritoResponse agregarLinea(String slug, String token, Long clienteId, AgregarLineaRequest req) {
		Tienda tienda = tiendas.findBySlug(slug)
				.orElseThrow(() -> new NoEncontradoException("tienda"));
		Carrito carrito = carritos.findByToken(token)
				.orElseThrow(() -> new NoEncontradoException("carrito"));
		Cliente cliente = clienteId == null ? null : clientes.findById(clienteId)
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		validarTenantYTitular(carrito, tienda, cliente);
		if (!Carrito.ACTIVO.equals(carrito.getEstado())) {
			throw new ConflictoException("carrito no activo");
		}
		if (cliente != null && carrito.getCliente() == null) {
			carrito.setCliente(cliente);
			carritos.save(carrito);
		}
		ProductoVariante variante = variantes.findById(req.varianteId())
				.orElseThrow(() -> new NoEncontradoException("variante"));
		if (!variante.comprable()) {
			throw new ConflictoException("variante no comprable");
		}
		if (!variante.getProducto().getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("variante");
		}
		CarritoItem linea = items.findByCarritoIdAndVarianteId(carrito.getId(), variante.getId())
				.orElseGet(() -> {
					CarritoItem nueva = new CarritoItem();
					nueva.setCarrito(carrito);
					nueva.setVariante(variante);
					nueva.setCantidad(0);
					return nueva;
				});
		linea.setCantidad(linea.getCantidad() + req.cantidad());
		linea.setPrecioUnitario(variante.getPrecio());
		items.save(linea);
		return ver(carrito.getId());
	}

	@Transactional(readOnly = true)
	public CarritoResponse ver(Long carritoId) {
		Carrito carrito = carritos.findById(carritoId)
				.orElseThrow(() -> new NoEncontradoException("carrito"));
		List<CarritoItem> itemsCarrito = items.findByCarritoId(carritoId);
		if (itemsCarrito.stream().anyMatch(l -> !l.getVariante().getProducto().getTienda().getId()
				.equals(carrito.getTienda().getId()))) {
			throw new NoEncontradoException("carrito");
		}
		List<LineaResponse> lineas = itemsCarrito.stream()
				.map(l -> new LineaResponse(
						l.getVariante().getId(),
						l.getVariante().getSku(),
						l.getCantidad(),
						l.getPrecioUnitario(),
						l.getPrecioUnitario().multiply(BigDecimal.valueOf(l.getCantidad()))))
				.toList();
		BigDecimal subtotal = lineas.stream()
				.map(LineaResponse::total)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CarritoResponse(carrito.getId(), carrito.getToken(), carrito.getEstado(), lineas, subtotal);
	}

	private Carrito nuevoCarrito(Tienda tienda, Cliente cliente) {
		Carrito carrito = new Carrito();
		carrito.setTienda(tienda);
		if (cliente != null) {
			carrito.setCliente(cliente);
		}
		byte[] bytes = new byte[24];
		aleatorio.nextBytes(bytes);
		carrito.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
		carrito.setMoneda("PEN");
		carrito.setEstado(Carrito.ACTIVO);
		carrito.setCreadoEn(Instant.now());
		carrito.setActualizadoEn(Instant.now());
		carrito.setExpiraEn(Instant.now().plusSeconds(7 * 24 * 60 * 60));
		return carritos.save(carrito);
	}

	private void validarTenantYTitular(Carrito carrito, Tienda tienda, Cliente cliente) {
		if (!carrito.getTienda().getId().equals(tienda.getId())
				|| (cliente != null && !cliente.getTienda().getId().equals(tienda.getId()))
				|| (carrito.getCliente() != null
						&& !carrito.getCliente().getTienda().getId().equals(tienda.getId()))) {
			throw new NoEncontradoException("carrito");
		}
		if (cliente != null && carrito.getCliente() != null
				&& !carrito.getCliente().getId().equals(cliente.getId())) {
			throw new NoEncontradoException("carrito");
		}
	}
}
