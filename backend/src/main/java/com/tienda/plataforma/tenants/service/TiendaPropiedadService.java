package com.tienda.plataforma.tenants.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.exception.ProhibidoException;
import com.tienda.plataforma.shared.exception.SolicitudInvalidaException;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

import jakarta.persistence.EntityManager;

/**
 * Propiedad de tienda (ADR-016, plan v5): exactamente un STORE_OWNER.
 * Toda mutación corre bajo `fn_lock_tienda_owner()` —la misma llave que
 * usará el trigger V22b— así que transferencias/confirmaciones concurrentes
 * se serializan por tienda y el conteo nunca queda en 0 ni en 2.
 */
@Service
public class TiendaPropiedadService {

	private final TiendaUsuarioRepository vinculos;
	private final UsuarioRepository usuarios;
	private final AuditoriaService auditoria;
	private final EntityManager entityManager;

	public TiendaPropiedadService(TiendaUsuarioRepository vinculos,
			UsuarioRepository usuarios, AuditoriaService auditoria,
			EntityManager entityManager) {
		this.vinculos = vinculos;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
		this.entityManager = entityManager;
	}

	/**
	 * El owner actual traspasa la propiedad a un miembro activo, en una TX:
	 * el antiguo baja a `admin` y el nuevo sube a `store_owner`.
	 */
	@Transactional
	public void transferir(Long tiendaId, Long actorId, Long nuevoOwnerId) {
		bloquear(tiendaId);
		TiendaUsuario actual = vinculos.findByTiendaIdAndUsuarioId(tiendaId, actorId)
				.orElseThrow(() -> new NoAutorizadoException("sin membresía"));
		if (!TiendaUsuario.STORE_OWNER.equals(actual.getRolTienda())) {
			throw new ProhibidoException("solo el owner transfiere la propiedad");
		}
		if (actorId.equals(nuevoOwnerId)) {
			throw new SolicitudInvalidaException("ya eres el owner");
		}
		TiendaUsuario destino = vinculos.findByTiendaIdAndUsuarioId(tiendaId, nuevoOwnerId)
				.orElseThrow(() -> new SolicitudInvalidaException("debe ser miembro de la tienda"));
		Usuario usuario = usuarios.findById(nuevoOwnerId)
				.orElseThrow(() -> new NoEncontradoException("usuario"));
		if (!Boolean.TRUE.equals(usuario.getActivo())) {
			throw new SolicitudInvalidaException("usuario inactivo");
		}
		actual.setRolTienda(TiendaUsuario.ADMIN);
		destino.setRolTienda(TiendaUsuario.STORE_OWNER);
		vinculos.save(actual);
		vinculos.save(destino);
		auditoria.registrar("tienda_usuarios", destino.getId(), "tienda.transferir_owner");
	}

	/**
	 * SUPER_ADMIN confirma el owner de una tienda existente sin owner
	 * (proceso de verificación fuera de banda, D5). Si ya hay owner,
	 * 409: usar transferencia.
	 */
	@Transactional
	public void confirmar(Long tiendaId, Long usuarioId) {
		bloquear(tiendaId);
		boolean hayOwner = vinculos.findByTiendaId(tiendaId).stream()
				.anyMatch(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda()));
		if (hayOwner) {
			throw new ConflictoException("la tienda ya tiene owner (usar transferencia)");
		}
		TiendaUsuario vinculo = vinculos.findByTiendaIdAndUsuarioId(tiendaId, usuarioId)
				.orElseThrow(() -> new SolicitudInvalidaException("debe ser miembro de la tienda"));
		Usuario usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new NoEncontradoException("usuario"));
		if (!Boolean.TRUE.equals(usuario.getActivo())) {
			throw new SolicitudInvalidaException("usuario inactivo");
		}
		vinculo.setRolTienda(TiendaUsuario.STORE_OWNER);
		vinculos.save(vinculo);
		auditoria.registrar("tienda_usuarios", vinculo.getId(), "tienda.owner_confirmado");
	}

	private void bloquear(Long tiendaId) {
		entityManager.createNativeQuery("SELECT fn_lock_tienda_owner(:tienda)")
				.setParameter("tienda", tiendaId)
				.getSingleResult();
	}
}
