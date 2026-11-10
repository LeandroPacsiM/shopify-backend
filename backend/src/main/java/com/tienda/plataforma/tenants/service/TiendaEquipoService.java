package com.tienda.plataforma.tenants.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.AgregarMiembroRequest;
import com.tienda.plataforma.auth.dto.MiembroResponse;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/**
 * RF-ADMIN-04: equipo de la tienda. Solo `admin` (la ruta lo exige).
 * Agregar vincula un usuario ya registrado; revocar quita el vínculo sin
 * borrar el usuario (la BD prohíbe DELETE con historial). Reglas de
 * propiedad (ADR-016, plan v5): `store_owner` no se asigna aquí (solo vía
 * transferencia o confirmación SUPER_ADMIN); el único OWNER no se degrada
 * ni se revoca.
 */
@Service
public class TiendaEquipoService {

	private final TiendaUsuarioRepository vinculos;
	private final UsuarioRepository usuarios;
	private final TiendaRepository tiendas;
	private final AuditoriaService auditoria;

	public TiendaEquipoService(TiendaUsuarioRepository vinculos, UsuarioRepository usuarios,
			TiendaRepository tiendas, AuditoriaService auditoria) {
		this.vinculos = vinculos;
		this.usuarios = usuarios;
		this.tiendas = tiendas;
		this.auditoria = auditoria;
	}

	@Transactional(readOnly = true)
	public List<MiembroResponse> listar(Long tiendaId) {
		return vinculos.findByTiendaId(tiendaId).stream()
				.map(v -> new MiembroResponse(v.getUsuario().getId(),
						v.getUsuario().getEmail(), v.getRolTienda()))
				.toList();
	}

	@Transactional
	public MiembroResponse agregar(Long tiendaId, AgregarMiembroRequest req) {
		String rol = rolValido(req.rol());
		if (TiendaUsuario.STORE_OWNER.equals(rol)) {
			throw new ConflictoException("el owner solo llega por transferencia o confirmación");
		}
		Usuario usuario = usuarios.findByEmail(req.email().trim().toLowerCase())				.orElseThrow(() -> new NoEncontradoException("usuario"));
		if (vinculos.findByTiendaIdAndUsuarioId(tiendaId, usuario.getId()).isPresent()) {
			throw new ConflictoException("ya es miembro");
		}
		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tiendas.findById(tiendaId)
				.orElseThrow(() -> new NoEncontradoException("tienda")));
		vinculo.setUsuario(usuario);
		vinculo.setRolTienda(rol);
		vinculos.save(vinculo);
		auditoria.registrar("tienda_usuarios", vinculo.getId(), "tienda.agregar_miembro");
		return new MiembroResponse(usuario.getId(), usuario.getEmail(), rol);
	}

	@Transactional
	public MiembroResponse cambiarRol(Long tiendaId, Long usuarioId, String rol) {
		String nuevo = rolValido(rol);
		TiendaUsuario vinculo = vinculos.findByTiendaIdAndUsuarioId(tiendaId, usuarioId)
				.orElseThrow(() -> new NoEncontradoException("miembro"));
		if (TiendaUsuario.STORE_OWNER.equals(nuevo)
				&& !TiendaUsuario.STORE_OWNER.equals(vinculo.getRolTienda())) {
			throw new ConflictoException("el owner solo llega por transferencia o confirmación");
		}
		if (TiendaUsuario.STORE_OWNER.equals(vinculo.getRolTienda())
				&& !TiendaUsuario.STORE_OWNER.equals(nuevo)
				&& ownersDe(tiendaId) <= 1) {
			throw new ConflictoException("la tienda necesita su owner (transfiere primero)");
		}
		if (TiendaUsuario.ADMIN.equals(vinculo.getRolTienda())
				&& !TiendaUsuario.ADMIN.equals(nuevo)
				&& adminsDe(tiendaId) <= 1) {
			throw new ConflictoException("la tienda necesita un admin");
		}
		vinculo.setRolTienda(nuevo);
		auditoria.registrar("tienda_usuarios", vinculo.getId(), "tienda.cambiar_rol");
		return new MiembroResponse(usuarioId, vinculo.getUsuario().getEmail(), nuevo);
	}

	@Transactional
	public void revocar(Long tiendaId, Long usuarioId) {
		TiendaUsuario vinculo = vinculos.findByTiendaIdAndUsuarioId(tiendaId, usuarioId)
				.orElseThrow(() -> new NoEncontradoException("miembro"));
		if (TiendaUsuario.STORE_OWNER.equals(vinculo.getRolTienda()) && ownersDe(tiendaId) <= 1) {
			throw new ConflictoException("la tienda necesita su owner (transfiere primero)");
		}
		if (TiendaUsuario.ADMIN.equals(vinculo.getRolTienda()) && adminsDe(tiendaId) <= 1) {
			throw new ConflictoException("la tienda necesita un admin");
		}
		Long vinculoId = vinculo.getId();
		vinculos.delete(vinculo);
		auditoria.registrar("tienda_usuarios", vinculoId, "tienda.revocar_miembro");
	}

	private long ownersDe(Long tiendaId) {
		return vinculos.findByTiendaId(tiendaId).stream()
				.filter(v -> TiendaUsuario.STORE_OWNER.equals(v.getRolTienda()))
				.count();
	}

	private long adminsDe(Long tiendaId) {
		return vinculos.findByTiendaId(tiendaId).stream()
				.filter(v -> TiendaUsuario.ADMIN.equals(v.getRolTienda()))
				.count();
	}

	private String rolValido(String rol) {
		if (rol == null) {
			throw new ConflictoException("rol inválido");
		}
		String limpio = rol.trim().toLowerCase();
		if (!TiendaUsuario.STORE_OWNER.equals(limpio) && !TiendaUsuario.ADMIN.equals(limpio)
				&& !TiendaUsuario.OPERADOR.equals(limpio)) {
			throw new ConflictoException("rol inválido");
		}
		return limpio;
	}
}
