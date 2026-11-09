package com.tienda.plataforma.venta.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.tienda.plataforma.catalogo.entity.Producto;
import com.tienda.plataforma.catalogo.repository.ProductoRepository;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.entity.Direccion;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.service.PrivacidadService;
import com.tienda.plataforma.venta.dto.CrearDireccionRequest;
import com.tienda.plataforma.venta.dto.CrearDireccionRequest.DireccionResponse;
import com.tienda.plataforma.venta.dto.CrearResenaRequest;
import com.tienda.plataforma.venta.dto.PedidoResponse;
import com.tienda.plataforma.venta.dto.PerfilResponse;
import com.tienda.plataforma.venta.dto.ActualizarPerfilRequest;
import com.tienda.plataforma.venta.dto.ResenaPropiaResponse;
import com.tienda.plataforma.venta.dto.WishlistItemResponse;
import com.tienda.plataforma.venta.entity.ListaDeseoItem;
import com.tienda.plataforma.venta.entity.ListaDeseos;
import com.tienda.plataforma.venta.entity.Resena;
import com.tienda.plataforma.venta.repository.ListaDeseoItemRepository;
import com.tienda.plataforma.venta.repository.ListaDeseosRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;
import com.tienda.plataforma.venta.repository.ResenaRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Cuenta del cliente: direcciones, pedidos, wishlist y reseñas (RF-STORE-02, RF-REV). */
@RestController
@RequestMapping("/api/storefront/{slug}/cuenta")
@Tag(name = "Cuenta", description = "Direcciones, pedidos, wishlist y reseñas del cliente")
public class CuentaController {

	private final ClienteRepository clientes;
	private final TiendaRepository tiendas;
	private final DireccionRepository direcciones;
	private final PedidoRepository pedidos;
	private final ListaDeseosRepository listas;
	private final ListaDeseoItemRepository listaItems;
	private final ResenaRepository resenas;
	private final ProductoRepository productos;
	private final PrivacidadService privacidad;

	public CuentaController(ClienteRepository clientes, TiendaRepository tiendas, DireccionRepository direcciones,
			PedidoRepository pedidos, ListaDeseosRepository listas, ListaDeseoItemRepository listaItems,
			ResenaRepository resenas, ProductoRepository productos, PrivacidadService privacidad) {
		this.clientes = clientes;
		this.tiendas = tiendas;
		this.direcciones = direcciones;
		this.pedidos = pedidos;
		this.listas = listas;
		this.listaItems = listaItems;
		this.resenas = resenas;
		this.productos = productos;
		this.privacidad = privacidad;
	}

	@GetMapping("/direcciones")
	public List<DireccionResponse> direcciones(@PathVariable String slug) {
		return direcciones.findByClienteId(clienteDe(slug).getId()).stream()
				.map(d -> new DireccionResponse(d.getId(), d.getLinea1(), d.getCiudad(), d.getPais(),
						Boolean.TRUE.equals(d.getEsPrincipal())))
				.toList();
	}

	@PostMapping("/direcciones")
	@Transactional
	public DireccionResponse crearDireccion(@PathVariable String slug,
			@Valid @RequestBody CrearDireccionRequest req) {
		Cliente cliente = clienteDe(slug);
		Direccion d = new Direccion();
		d.setCliente(cliente);
		d.setLinea1(req.linea1());
		d.setLinea2(req.linea2());
		d.setCiudad(req.ciudad());
		d.setDepartamento(req.departamento());
		d.setCodigoPostal(req.codigoPostal());
		d.setPais(req.pais());
		d.setEsPrincipal(req.esPrincipal());
		d.setCreadoEn(Instant.now());
		direcciones.save(d);
		return new DireccionResponse(d.getId(), d.getLinea1(), d.getCiudad(), d.getPais(), d.getEsPrincipal());
	}

	@DeleteMapping("/direcciones/{id}")
	@Transactional
	public void borrarDireccion(@PathVariable String slug, @PathVariable Long id) {
		Cliente cliente = clienteDe(slug);
		Direccion d = direcciones.findById(id).orElseThrow(() -> new NoEncontradoException("dirección"));
		if (!d.getCliente().getId().equals(cliente.getId())) {
			throw new NoEncontradoException("dirección");
		}
		direcciones.delete(d);
	}

	@GetMapping("/pedidos")
	public List<PedidoResponse> pedidos(@PathVariable String slug) {
		Cliente cliente = clienteDe(slug);
		return pedidos.findByClienteId(cliente.getId()).stream()
				.filter(p -> p.getTienda().getId().equals(cliente.getTienda().getId()))
				.map(p -> new PedidoResponse(p.getId(), p.getNumeroPedido(), p.getEstado(), p.getTotal()))
				.toList();
	}

	@GetMapping("/perfil")
	public PerfilResponse perfil(@PathVariable String slug) {
		Cliente cliente = clienteDe(slug);
		return new PerfilResponse(cliente.getNombre(), cliente.getApellido(),
				cliente.getTelefono(), cliente.getAceptaMarketing());
	}

	@PatchMapping("/perfil")
	@Transactional
	public PerfilResponse actualizarPerfil(@PathVariable String slug,
			@Valid @RequestBody ActualizarPerfilRequest req) {
		Cliente cliente = clienteDe(slug);
		if (req.nombre() != null) {
			cliente.setNombre(normalizar(req.nombre()));
		}
		if (req.apellido() != null) {
			cliente.setApellido(normalizar(req.apellido()));
		}
		if (req.telefono() != null) {
			cliente.setTelefono(normalizar(req.telefono()));
		}
		if (req.aceptaMarketing() != null) {
			cliente.setAceptaMarketing(req.aceptaMarketing());
		}
		return new PerfilResponse(cliente.getNombre(), cliente.getApellido(),
				cliente.getTelefono(), cliente.getAceptaMarketing());
	}

	@PostMapping("/wishlist/{productoId}")
	@Transactional
	public void agregarWishlist(@PathVariable String slug, @PathVariable Long productoId) {
		Cliente cliente = clienteDe(slug);
		Producto producto = productos.findById(productoId)
				.orElseThrow(() -> new NoEncontradoException("producto"));
		if (!producto.getTienda().getId().equals(cliente.getTienda().getId())) {
			throw new NoEncontradoException("producto");
		}
		ListaDeseos lista = listas.findByClienteIdAndNombre(cliente.getId(), "Favoritos")
				.orElseGet(() -> {
					ListaDeseos nueva = new ListaDeseos();
					nueva.setCliente(cliente);
					nueva.setNombre("Favoritos");
					nueva.setCreadoEn(Instant.now());
					return listas.save(nueva);
				});
		boolean existe = listaItems.findByListaDeseosId(lista.getId()).stream()
				.anyMatch(i -> i.getProducto().getId().equals(productoId));
		if (!existe) {
			ListaDeseoItem item = new ListaDeseoItem();
			item.setListaDeseos(lista);
			item.setProducto(producto);
			item.setCreadoEn(Instant.now());
			listaItems.save(item);
		}
	}

	@GetMapping("/wishlist")
	public List<WishlistItemResponse> wishlist(@PathVariable String slug) {
		Cliente cliente = clienteDe(slug);
		return listas.findByClienteIdAndNombre(cliente.getId(), "Favoritos")
				.map(l -> listaItems.findByListaDeseosId(l.getId()).stream()
						.map(i -> new WishlistItemResponse(i.getProducto().getId(),
								i.getProducto().getTitulo(), i.getProducto().getSlug()))
						.toList())
				.orElse(List.of());
	}

	@DeleteMapping("/wishlist/{productoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void quitarWishlist(@PathVariable String slug, @PathVariable Long productoId) {
		Cliente cliente = clienteDe(slug);
		ListaDeseos lista = listas.findByClienteIdAndNombre(cliente.getId(), "Favoritos")
				.orElseThrow(() -> new NoEncontradoException("wishlist"));
		ListaDeseoItem item = listaItems.findByListaDeseosId(lista.getId()).stream()
				.filter(i -> i.getProducto().getId().equals(productoId))
				.findFirst()
				.orElseThrow(() -> new NoEncontradoException("wishlist"));
		listaItems.delete(item);
	}

	@GetMapping("/resenas")
	public List<ResenaPropiaResponse> misResenas(@PathVariable String slug) {
		Cliente cliente = clienteDe(slug);
		return resenas.findByClienteId(cliente.getId()).stream()
				.map(r -> new ResenaPropiaResponse(r.getId(),
						r.getProducto() == null ? null : r.getProducto().getId(),
						r.getCalificacion(), r.getTitulo(), r.getComentario(), r.getAprobada()))
				.toList();
	}

	@PostMapping("/resenas")
	@Transactional
	public void crearResena(@PathVariable String slug, @Valid @RequestBody CrearResenaRequest req) {
		Cliente cliente = clienteDe(slug);
		Producto producto = productos.findById(req.productoId())
				.orElseThrow(() -> new NoEncontradoException("producto"));
		if (!producto.getTienda().getId().equals(cliente.getTienda().getId())) {
			throw new NoEncontradoException("producto");
		}
		if (resenas.findByProductoIdAndClienteId(producto.getId(), cliente.getId()).isPresent()) {
			throw new ConflictoException("ya reseñaste este producto");
		}
		Resena resena = new Resena();
		resena.setProducto(producto);
		resena.setCliente(cliente);
		resena.setCalificacion(req.calificacion());
		resena.setTitulo(req.titulo());
		resena.setComentario(req.comentario());
		resena.setAprobada(false);
		resena.setCreadoEn(Instant.now());
		resenas.save(resena);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void anonimizar(@PathVariable String slug) {
		Cliente cliente = clienteDe(slug);
		privacidad.anonimizarCuenta(cliente.getUsuario().getId(), slug);
	}

	private Cliente clienteDe(String slug) {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (!(principal instanceof Long usuarioId)) {
			throw new NoAutorizadoException("sin autenticación");
		}
		Tienda tienda = tiendas.findBySlug(slug).orElseThrow(() -> new NoEncontradoException("tienda"));
		Cliente cliente = clientes.findByUsuarioId(usuarioId)
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		if (!cliente.getTienda().getId().equals(tienda.getId())) {
			throw new NoEncontradoException("tienda");
		}
		return cliente;
	}

	private String normalizar(String valor) {
		String limpio = valor.trim();
		return limpio.isEmpty() ? null : limpio;
	}
}
