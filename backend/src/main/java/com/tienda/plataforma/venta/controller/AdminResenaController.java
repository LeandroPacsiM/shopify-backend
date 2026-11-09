package com.tienda.plataforma.venta.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.exception.NoEncontradoException;
import com.tienda.plataforma.shared.security.TenantContext;
import com.tienda.plataforma.venta.dto.ResenaAdminResponse;
import com.tienda.plataforma.venta.entity.Resena;
import com.tienda.plataforma.venta.repository.ResenaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Admin de tienda: modera reseñas (RF-REV-01). Solo reseñas de su tienda. */
@RestController
@RequestMapping("/api/admin/resenas")
@Tag(name = "Admin reseñas", description = "Moderación de reseñas de la tienda")
public class AdminResenaController {

	private final ResenaRepository resenas;

	public AdminResenaController(ResenaRepository resenas) {
		this.resenas = resenas;
	}

	@GetMapping
	@Operation(summary = "Lista reseñas de la tienda, opcionalmente por estado de aprobación")
	public List<ResenaAdminResponse> listar(@RequestParam(required = false) Boolean aprobada) {
		return resenas.findByProductoTiendaId(tiendaId()).stream()
				.filter(r -> aprobada == null || aprobada.equals(r.getAprobada()))
				.map(this::responder)
				.toList();
	}

	@PatchMapping("/{id}/aprobar")
	@Operation(summary = "Publica una reseña")
	@Transactional
	public ResenaAdminResponse aprobar(@PathVariable Long id) {
		Resena resena = deLaTienda(id);
		resena.setAprobada(true);
		return responder(resena);
	}

	@PatchMapping("/{id}/ocultar")
	@Operation(summary = "Oculta una reseña publicada")
	@Transactional
	public ResenaAdminResponse ocultar(@PathVariable Long id) {
		Resena resena = deLaTienda(id);
		resena.setAprobada(false);
		return responder(resena);
	}

	private Resena deLaTienda(Long id) {
		Resena resena = resenas.findById(id).orElseThrow(() -> new NoEncontradoException("reseña"));
		if (resena.getProducto() == null
				|| !resena.getProducto().getTienda().getId().equals(tiendaId())) {
			throw new NoEncontradoException("reseña");
		}
		return resena;
	}

	private ResenaAdminResponse responder(Resena r) {
		return new ResenaAdminResponse(r.getId(),
				r.getProducto() == null ? null : r.getProducto().getId(),
				r.getProducto() == null ? null : r.getProducto().getTitulo(),
				r.getCalificacion(), r.getTitulo(), r.getComentario(), r.getAprobada());
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
