package com.tienda.plataforma.pagos.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.pagos.dto.PasarelaResponse;
import com.tienda.plataforma.pagos.dto.RegistrarPasarelaRequest;
import com.tienda.plataforma.pagos.service.TiendaPasarelaService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin: credenciales de pasarela de su tienda (RF-PAY-01). Sin secretos en respuestas. */
@RestController
@RequestMapping("/api/admin/pasarelas")
@Tag(name = "Admin pasarelas", description = "Registro de credenciales por tienda")
public class AdminPasarelaController {

	private final TiendaPasarelaService pasarelas;

	public AdminPasarelaController(TiendaPasarelaService pasarelas) {
		this.pasarelas = pasarelas;
	}

	@GetMapping
	@Operation(summary = "Lista pasarelas de la tienda (sin secretos)")
	public List<PasarelaResponse> listar() {
		return pasarelas.listar(tiendaId());
	}

	@PostMapping
	@Operation(summary = "Registra o rota credenciales (solo admin de la tienda)")
	public PasarelaResponse registrar(@Valid @RequestBody RegistrarPasarelaRequest req) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		Object principal = auth == null ? null : auth.getPrincipal();
		if (!(principal instanceof Long usuarioId)) {
			throw new NoAutorizadoException("sin autenticación");
		}
		boolean esSuper = auth.getAuthorities().stream()
				.anyMatch(a -> a.getAuthority().equals("ROLE_super_admin"));
		return pasarelas.registrar(usuarioId, tiendaId(), esSuper, req);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
