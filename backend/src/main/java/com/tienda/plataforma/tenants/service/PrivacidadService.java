package com.tienda.plataforma.tenants.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.CodigoRecuperacionRepository;
import com.tienda.plataforma.auth.repository.SesionRepository;
import com.tienda.plataforma.auth.repository.TokenCuentaRepository;
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
 * GDPR (RF-ORDER-03, RNF-LEG-01): el cliente puede irse, sus datos no.
 * Anonimiza PII (usuario, cliente, direcciones, contacto de pedidos y
 * checkouts, reseñas) y revoca sesiones/códigos/tokens; pedidos, auditoría e
 * historial se conservan (la BD prohíbe DELETE con historial).
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
	private final SesionRepository sesiones;
	private final CodigoRecuperacionRepository codigos;
	private final TokenCuentaRepository tokens;
	private final PasswordEncoder passwordEncoder;

	public PrivacidadService(ClienteRepository clientes, DireccionRepository direcciones,
			PedidoRepository pedidos, CheckoutSesionRepository checkouts, PedidoDireccionRepository pedidoDirecciones,
			ResenaRepository resenas, SesionRepository sesiones,
			CodigoRecuperacionRepository codigos, TokenCuentaRepository tokens,
			PasswordEncoder passwordEncoder) {
		this.clientes = clientes;
		this.direcciones = direcciones;
		this.pedidos = pedidos;
		this.checkouts = checkouts;
		this.pedidoDirecciones = pedidoDirecciones;
		this.resenas = resenas;
		this.sesiones = sesiones;
		this.codigos = codigos;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void anonimizarCuenta(Long usuarioId, String slug) {
		Cliente cliente = clientes.findByUsuarioId(usuarioId)
				.filter(c -> c.getTienda().getSlug().equals(slug))
				.orElseThrow(() -> new NoEncontradoException("cliente"));
		Usuario usuario = cliente.getUsuario();
		Instant ahora = Instant.now();

		usuario.setEmail("eliminado-" + usuario.getId() + "@anonimo.local");
		usuario.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		usuario.setNombre("Eliminado");
		usuario.setApellido("Eliminado");
		usuario.setActivo(false);
		usuario.setTotpSecret(null);
		usuario.setTotpHabilitado(false);
		usuario.setTotpConfirmadoEn(null);
		usuario.setIntentosFallidos(0);
		usuario.setBloqueadoHasta(null);

		sesiones.findByUsuarioId(usuario.getId()).forEach(s -> {
			if (s.getRevocadoEn() == null) {
				s.setRevocadoEn(ahora);
				s.setMotivoRevocacion("baja_gdpr");
			}
		});
		codigos.findByUsuarioId(usuario.getId()).forEach(c -> c.consumir(ahora));
		tokens.findByUsuarioId(usuario.getId()).forEach(t -> {
			if (t.getUsadoEn() == null) {
				t.consumir(ahora);
			}
		});

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
