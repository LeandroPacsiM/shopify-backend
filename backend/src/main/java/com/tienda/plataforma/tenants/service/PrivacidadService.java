package com.tienda.plataforma.tenants.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.repository.ClienteSesionRepository;
import com.tienda.plataforma.auth.repository.CodigoClienteRepository;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Cliente;
import com.tienda.plataforma.tenants.repository.ClienteRepository;
import com.tienda.plataforma.tenants.repository.DireccionRepository;
import com.tienda.plataforma.venta.entity.Pedido;
import com.tienda.plataforma.venta.repository.CheckoutSesionRepository;
import com.tienda.plataforma.venta.repository.PedidoDireccionRepository;
import com.tienda.plataforma.venta.repository.PedidoRepository;
import com.tienda.plataforma.venta.repository.ResenaRepository;

/**
 * GDPR (RF-ORDER-03, RNF-LEG-01): el CUSTOMER puede irse, sus datos no.
 * Anonimiza PII (cliente, direcciones, contacto de pedidos y checkouts,
 * reseñas) y revoca sesiones/códigos CUSTOMER; pedidos, auditoría e
 * historial se conservan (la BD prohíbe DELETE con historial).
 * No toca `usuarios`: el ámbito staff es independiente (ADR-016).
 * El JWT de acceso vive 15 min más como máximo.
 */
@Service
public class PrivacidadService {

	private final ClienteRepository clientes;
	private final DireccionRepository direcciones;
	private final PedidoRepository pedidos;
	private final CheckoutSesionRepository checkouts;
	private final PedidoDireccionRepository pedidoDirecciones;
	private final ResenaRepository resenas;
	private final ClienteSesionRepository clienteSesiones;
	private final CodigoClienteRepository clienteCodigos;

	public PrivacidadService(ClienteRepository clientes, DireccionRepository direcciones,
			PedidoRepository pedidos, CheckoutSesionRepository checkouts, PedidoDireccionRepository pedidoDirecciones,
			ResenaRepository resenas,
			ClienteSesionRepository clienteSesiones, CodigoClienteRepository clienteCodigos) {
		this.clientes = clientes;
		this.direcciones = direcciones;
		this.pedidos = pedidos;
		this.checkouts = checkouts;
		this.pedidoDirecciones = pedidoDirecciones;
		this.resenas = resenas;
		this.clienteSesiones = clienteSesiones;
		this.clienteCodigos = clienteCodigos;
	}

	@Transactional
	public void anonimizarCliente(Long clienteId, String slug) {
		Cliente cliente = clientes.findById(clienteId)
				.filter(c -> c.getTienda().getSlug().equals(slug))
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		Instant ahora = Instant.now();

		clienteSesiones.findByClienteId(cliente.getId()).forEach(s -> {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(ahora);
				s.setMotivoRevocacion("baja_gdpr");
			}
		});
		clienteCodigos.findByTiendaIdAndEmailAndUsadoEnIsNullOrderByCreadoEnDesc(
				cliente.getTienda().getId(), cliente.getEmail()).forEach(c -> c.consumir(ahora));

		cliente.setNombre("Eliminado");
		cliente.setApellido("Eliminado");
		cliente.setTelefono(null);
		cliente.setEmail("eliminado-" + cliente.getId() + "@anonimo.local");

		direcciones.findByClienteId(cliente.getId()).forEach(d -> {
			d.setLinea1("Eliminada");
			d.setLinea2(null);
			d.setCiudad(null);
			d.setDepartamento(null);
			d.setCodigoPostal(null);
			d.setPais(null);
		});

		for (Pedido pedido : pedidos.findByClienteId(cliente.getId())) {
			pedido.setEmailContacto(null);
			pedido.setTelefonoContacto(null);
			pedido.setNotasCliente(null);
		}
		checkouts.findByClienteId(cliente.getId()).forEach(c -> {
			c.setEmailContacto(null);
			c.setTelefonoContacto(null);
			c.setNotasCliente(null);
		});
		for (Pedido pedido : pedidos.findByClienteId(cliente.getId())) {
			pedidoDirecciones.findByPedidoId(pedido.getId()).forEach(d -> {
				d.setNombreDestinatario("Eliminado");
				d.setLinea1("Eliminada");
				d.setLinea2(null);
				d.setCiudad(null);
				d.setDepartamento(null);
				d.setCodigoPostal(null);
				d.setPais(null);
				d.setTelefono(null);
			});
		}

		resenas.findByClienteId(cliente.getId()).forEach(r -> {
			r.setTitulo(null);
			r.setComentario(null);
		});
	}
}
