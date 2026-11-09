package com.tienda.plataforma.realtime.controller;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.realtime.NotificacionService;
import com.tienda.plataforma.realtime.dto.NoLeidasResponse;
import com.tienda.plataforma.realtime.dto.NotificacionResponse;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Bandeja admin: lo que la campana WS avisó queda aquí (V16, RF-ADMIN-05). */
@RestController
@RequestMapping("/api/admin/notificaciones")
@Tag(name = "Admin notificaciones", description = "Bandeja persistente de la tienda")
public class NotificacionController {

	private final NotificacionService notificaciones;

	public NotificacionController(NotificacionService notificaciones) {
		this.notificaciones = notificaciones;
	}

	@GetMapping
	@Operation(summary = "Lista avisos de la tienda, recientes primero (paginado)")
	public Page<NotificacionResponse> listar(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return notificaciones.listar(tiendaId(), page, size);
	}

	@GetMapping("/no-leidas")
	@Operation(summary = "Cuenta avisos sin leer (badge de la campana)")
	public NoLeidasResponse noLeidas() {
		return notificaciones.contarNoLeidas(tiendaId());
	}

	@PatchMapping("/{id}/leer")
	@Operation(summary = "Marca un aviso como leído")
	public NotificacionResponse leer(@PathVariable Long id) {
		return notificaciones.marcarLeida(tiendaId(), id);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
