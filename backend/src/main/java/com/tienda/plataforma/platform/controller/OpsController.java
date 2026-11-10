package com.tienda.plataforma.platform.controller;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.auth.entity.RegistroAuditoria;
import com.tienda.plataforma.auth.entity.Usuario;
import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;
import com.tienda.plataforma.platform.facturacion.repository.SuscripcionRepository;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.tenants.entity.Tienda;
import com.tienda.plataforma.tenants.repository.TiendaRepository;
import com.tienda.plataforma.tenants.service.TiendaPropiedadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Panel plataforma (RF-OPS): lista y agregados de tenants, compliance,
 * auditoría. Nunca datos internos de tiendas (RF-TENANT-03).
 *
 * Decisión OPS-01: la API vive en `/api/ops` (RF-TENANT-03 manda sobre la
 * ruta); las protecciones de RNF-SEG-05 se aplican aquí: falso 404 ante
 * no autorizados (ver `SecurityConfig`) y lista opcional de IPs
 * (`app.ops.ips-permitidas`, vacía = sin restricción). `/ops-core-management`
 * queda como nombre de ruta del SPA, no de la API.
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
	private final UsuarioRepository usuarios;
	private final TiendaPropiedadService propiedad;
	private final HttpServletRequest request;
	private final List<String> ipsPermitidas;

	public OpsController(TiendaRepository tiendas, RegistroAuditoriaRepository auditoria,
			SuscripcionRepository suscripciones, UsuarioRepository usuarios,
			TiendaPropiedadService propiedad,
			HttpServletRequest request,
			@Value("${app.ops.ips-permitidas:}") String ipsPermitidas) {
		this.tiendas = tiendas;
		this.auditoria = auditoria;
		this.suscripciones = suscripciones;
		this.usuarios = usuarios;
		this.propiedad = propiedad;
		this.request = request;
		this.ipsPermitidas = Arrays.stream(ipsPermitidas.split(",")).map(String::trim)
				.filter(s -> !s.isEmpty()).toList();
	}

	@GetMapping("/tiendas")
	@Operation(summary = "Lista tenants con su estado (agregados, sin datos internos)")
	public Page<TiendaResumen> tiendas(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		superAdmin();
		return tiendas.findAll(paginacion(page, size, "id")).map(t -> resumir(t));
	}

	@PatchMapping("/tiendas/{id}/suspender")
	@Transactional
	@Operation(summary = "Suspende una tienda por compliance (desactivar, con motivo en auditoría)")
	public TiendaResumen suspender(@PathVariable Long id) {
		Long actor = superAdmin();
		Tienda tienda = tiendas.findById(id).orElseThrow(() -> new NoEncontradoException("tienda"));
		tienda.setActiva(false);
		tienda.setActualizadoEn(Instant.now());
		tiendas.save(tienda);
		auditar("tiendas", id, "suspender", actor);
		return resumir(tienda);
	}

	@PatchMapping("/tiendas/{id}/reactivar")
	@Transactional
	@Operation(summary = "Reactiva una tienda (atiende apelación)")
	public TiendaResumen reactivar(@PathVariable Long id) {
		Long actor = superAdmin();
		Tienda tienda = tiendas.findById(id).orElseThrow(() -> new NoEncontradoException("tienda"));
		tienda.setActiva(true);
		tienda.setActualizadoEn(Instant.now());
		tiendas.save(tienda);
		auditar("tiendas", id, "reactivar", actor);
		return resumir(tienda);
	}

	@GetMapping("/auditoria")
	@Operation(summary = "Auditoría cruzada de acciones de plataforma (paginada)")
	public Page<AuditoriaResumen> auditoria(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		superAdmin();
		return auditoria.findAll(paginacion(page, size, "creadoEn")).map(a -> new AuditoriaResumen(a.getId(),
				a.getUsuario() == null ? null : a.getUsuario().getId(),
				a.getEntidad(), a.getEntidadId(), a.getAccion(), a.getCreadoEn()));
	}

	public record ConfirmarOwnerRequest(Long usuarioId) {
	}

	@PatchMapping("/tiendas/{id}/propietario")
	@Operation(summary = "Confirma el STORE_OWNER de una tienda existente (verificación fuera de banda, D5)")
	public TiendaResumen confirmarOwner(@PathVariable Long id,
			@jakarta.validation.Valid @RequestBody ConfirmarOwnerRequest req) {
		Long actor = superAdmin();
		Tienda tienda = tiendas.findById(id).orElseThrow(() -> new NoEncontradoException("tienda"));
		propiedad.confirmar(id, req.usuarioId());
		auditar("tiendas", id, "owner_confirmado", actor);
		return resumir(tienda);
	}

	private PageRequest paginacion(Integer pagina, Integer tamano, String ordenarPor) {
		int page = pagina == null || pagina < 0 ? 0 : pagina;
		int size = tamano == null ? 20 : Math.min(Math.max(tamano, 1), 50);
		return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, ordenarPor));
	}

	private Long superAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		Object principal = auth == null ? null : auth.getPrincipal();
		boolean es = auth != null && principal instanceof Long
				&& auth.getAuthorities().stream()
						.anyMatch(a -> a.getAuthority().equals("ROLE_super_admin"));
		if (!es) {
			throw new NoEncontradoException("no encontrado");
		}
		if (!ipsPermitidas.isEmpty() && !ipsPermitidas.contains(request.getRemoteAddr())) {
			throw new NoEncontradoException("no encontrado");
		}
		return (Long) principal;
	}

	private TiendaResumen resumir(Tienda tienda) {
		String suscripcion = suscripciones.findByTiendaId(tienda.getId())
				.map(s -> s.getPlan() + "/" + s.getEstado()).orElse(null);
		return new TiendaResumen(tienda.getId(), tienda.getNombre(), tienda.getSlug(),
				Boolean.TRUE.equals(tienda.getActiva()), suscripcion);
	}

	private void auditar(String entidad, Long entidadId, String accion, Long actorId) {
		Usuario actor = usuarios.findById(actorId)
				.orElseThrow(() -> new NoEncontradoException("no encontrado"));
		RegistroAuditoria fila = new RegistroAuditoria();
		fila.setUsuario(actor);
		fila.setEntidad(entidad);
		fila.setEntidadId(entidadId);
		fila.setAccion("ops." + accion);
		fila.setIp(request.getRemoteAddr());
		fila.setCreadoEn(Instant.now());
		auditoria.save(fila);
	}
}
