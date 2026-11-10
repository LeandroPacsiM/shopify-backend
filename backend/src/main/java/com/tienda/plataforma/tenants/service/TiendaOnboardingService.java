package com.tienda.plataforma.tenants.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tienda.plataforma.auth.dto.CrearTiendaRequest;
import com.tienda.plataforma.auth.dto.MiTiendaResponse;
import com.tienda.plataforma.auth.dto.TiendaCreadaResponse;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.platform.facturacion.entity.Suscripcion;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.auditoria.AuditoriaService;
import com.tienda.plataforma.shared.exception.ConflictoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.entity.TiendaUsuario;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.repository.TiendaUsuarioRepository;

/**
 * RF-TENANT-04: onboarding de comerciante. La tienda nace activa con
 * suscripción pendiente (trial: setup permitido, checkout bloqueado por
 * `FacturacionService.exigirActiva` hasta suscribir). Sin cambio de esquema.
 */
@Service
public class TiendaOnboardingService {

	private final TiendaRepository tiendas;
	private final TiendaUsuarioRepository vinculos;
	private final SuscripcionRepository suscripciones;
	private final UsuarioRepository usuarios;
	private final AuditoriaService auditoria;

	public TiendaOnboardingService(TiendaRepository tiendas, TiendaUsuarioRepository vinculos,
			SuscripcionRepository suscripciones, UsuarioRepository usuarios,
			AuditoriaService auditoria) {
		this.tiendas = tiendas;
		this.vinculos = vinculos;
		this.suscripciones = suscripciones;
		this.usuarios = usuarios;
		this.auditoria = auditoria;
	}

	@Transactional
	public TiendaCreadaResponse crear(Long usuarioId, CrearTiendaRequest req) {
		var usuario = usuarios.findById(usuarioId)
				.orElseThrow(() -> new NoEncontradoException("usuario"));
		String slug = req.slug().trim().toLowerCase();
		if (tiendas.findBySlug(slug).isPresent()) {
			throw new ConflictoException("slug en uso");
		}
		Tienda tienda = new Tienda();
		tienda.setNombre(req.nombre().trim());
		tienda.setSlug(slug);
		tienda.setDescripcion(req.descripcion());
		tienda.setActiva(true);
		tiendas.save(tienda);

		TiendaUsuario vinculo = new TiendaUsuario();
		vinculo.setTienda(tienda);
		vinculo.setUsuario(usuario);
		vinculo.setRolTienda(TiendaUsuario.STORE_OWNER);
		vinculos.save(vinculo);

		Suscripcion suscripcion = new Suscripcion();
		suscripcion.setTienda(tienda);
		suscripcion.setPlan(Suscripcion.MENSUAL);
		suscripcion.setEstado(Suscripcion.PENDIENTE);
		suscripciones.save(suscripcion);

		auditoria.registrar("tiendas", tienda.getId(), "tienda.crear");
		return new TiendaCreadaResponse(tienda.getId(), tienda.getNombre(), tienda.getSlug(),
				tienda.getActiva());
	}

	/**
	 * Mis tiendas: membresías del usuario con datos básicos para elegir
	 * contexto. Solo lectura, sin esquema nuevo. Funciona con JWT limitado
	 * (trial) y completo.
	 */
	@Transactional(readOnly = true)
	public java.util.List<MiTiendaResponse> misTiendas(Long usuarioId) {
		return vinculos.findByUsuarioId(usuarioId).stream()
				.map(v -> new MiTiendaResponse(v.getTienda().getId(), v.getTienda().getNombre(),
						v.getTienda().getSlug(), v.getTienda().getActiva(), v.getRolTienda()))
				.toList();
	}
}
