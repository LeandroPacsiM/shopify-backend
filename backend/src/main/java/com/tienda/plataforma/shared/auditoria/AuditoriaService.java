package com.tienda.plataforma.shared.auditoria;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.tienda.plataforma.auth.entity.RegistroAuditoria;
import com.tienda.plataforma.auth.repository.RegistroAuditoriaRepository;
import com.tienda.plataforma.auth.repository.UsuarioRepository;

/**
 * RNF-LEG/RF-TENANT: la BD no audita sola; el backend escribe la fila en la
 * misma transacción del cambio (sin secretos ni PII en `datos`). Sin actor
 * (webhook) o sin request (job/test), esos campos quedan nulos.
 */
@Service
public class AuditoriaService {

	private final RegistroAuditoriaRepository auditoria;
	private final UsuarioRepository usuarios;

	public AuditoriaService(RegistroAuditoriaRepository auditoria, UsuarioRepository usuarios) {
		this.auditoria = auditoria;
		this.usuarios = usuarios;
	}

	public void registrar(String entidad, Long entidadId, String accion) {
		RegistroAuditoria fila = new RegistroAuditoria();
		fila.setEntidad(entidad);
		fila.setEntidadId(entidadId);
		fila.setAccion(accion);
		actor().flatMap(usuarios::findById).ifPresent(fila::setUsuario);
		fila.setIp(ip());
		fila.setCreadoEn(Instant.now());
		auditoria.save(fila);
	}

	private Optional<Long> actor() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		Object principal = auth == null ? null : auth.getPrincipal();
		return principal instanceof Long id ? Optional.of(id) : Optional.empty();
	}

	private String ip() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ServletRequestAttributes servlet) {
			return servlet.getRequest().getRemoteAddr();
		}
		return null;
	}
}
