package com.tienda.plataforma.platform.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.auth.entity.RegistroAuditoria;
import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Panel plataforma (RF-OPS): lista y agregados de tenants, compliance,
 * auditoría. Nunca datos internos de tiendas (RF-TENANT-03).
 */
@RestController
@RequestMapping("/api/ops")
@Tag(name = "Ops", description = "Panel del dueño de la plataforma (super_admin)")
public class OpsController {

	public record TiendaResumen(Long id, String nombre, String slug, boolean activa, String suscripcion) {
	}

	public record AuditoriaResumen(Long id, Long usuarioId, String entidad, Long entidadId,
			String accion, java.time.Instant creadoEn) {
	}

	private final TiendaRepository tiendas;
	private final RegistroAuditoriaRepository auditoria;
	private final SuscripcionRepository suscripciones;

	public OpsController(TiendaRepository tiendas, RegistroAuditoriaRepository auditoria,
			SuscripcionRepository suscripciones) {
		this.tiendas = tiendas;
		this.auditoria = auditoria;
		this.suscripciones = suscripciones;
	}

	@GetMapping("/tiendas")
	@Operation(summary = "Lista tenants con su estado (agregados, sin datos internos)")
	public List<TiendaResumen> tiendas() {
		superAdmin();
		return tiendas.findAll().stream()
				.map(t -> resumir(t))
				.toList();
	}

	@PatchMapping("/tiendas/{id}/suspender")
	@Transactional
	@Operation(summary = "Suspende una tienda por compliance (desactivar, con motivo en auditoría)")
	public TiendaResumen suspender(@PathVariable Long id) {
		superAdmin();
		Tienda tienda = tiendas.findById(id).orElseThrow(() -> new NoEncontradoException("tienda"));
		tienda.setActiva(false);
		tienda.setActualizadoEn(Instant.now());
		tiendas.save(tienda);
		auditar("tiendas", id, "suspender");
		return resumir(tienda);
	}

	@PatchMapping("/tiendas/{id}/reactivar")
	@Transactional
	@Operation(summary = "Reactiva una tienda (atiende apelación)")
	public TiendaResumen reactivar(@PathVariable Long id) {
		superAdmin();
		Tienda tienda = tiendas.findById(id).orElseThrow(() -> new NoEncontradoException("tienda"));
		tienda.setActiva(true);
		tienda.setActualizadoEn(Instant.now());
		tiendas.save(tienda);
		auditar("tiendas", id, "reactivar");
		return resumir(tienda);
	}

	@GetMapping("/auditoria")
	@Operation(summary = "Auditoría cruzada de acciones de plataforma")
	public List<AuditoriaResumen> auditoria() {
		superAdmin();
		return auditoria.findAll().stream()
				.map(a -> new AuditoriaResumen(a.getId(),
						a.getUsuario() == null ? null : a.getUsuario().getId(),
						a.getEntidad(), a.getEntidadId(), a.getAccion(), a.getCreadoEn()))
				.toList();
	}

	private void superAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		boolean es = auth != null && auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_super_admin"));
		if (!es) {
			throw new NoAutorizadoException("solo super_admin");
		}
	}

	private TiendaResumen resumir(Tienda tienda) {
		String suscripcion = suscripciones.findByTiendaId(tienda.getId())
				.map(s -> s.getPlan() + "/" + s.getEstado()).orElse(null);
		return new TiendaResumen(tienda.getId(), tienda.getNombre(), tienda.getSlug(),
				Boolean.TRUE.equals(tienda.getActiva()), suscripcion);
	}

	private void auditar(String entidad, Long entidadId, String accion) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		RegistroAuditoria fila = new RegistroAuditoria();
		fila.setEntidad(entidad);
		fila.setEntidadId(entidadId);
		fila.setAccion("ops." + accion);
		fila.setCreadoEn(Instant.now());
		auditoria.save(fila);
	}
}
