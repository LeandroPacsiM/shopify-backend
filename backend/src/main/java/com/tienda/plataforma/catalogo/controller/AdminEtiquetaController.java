package com.tienda.plataforma.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.tienda.plataforma.catalogo.dto.AsignarEtiquetaRequest;
import com.tienda.plataforma.catalogo.dto.CrearEtiquetaRequest;
import com.tienda.plataforma.catalogo.dto.EtiquetaResponse;
import com.tienda.plataforma.catalogo.service.EtiquetaService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: etiquetas y asignación a productos propios (RF-ADMIN-01). */
@RestController
@RequestMapping("/api/admin/etiquetas")
@Tag(name = "Admin etiquetas", description = "Etiquetas reutilizables y sus productos")
public class AdminEtiquetaController {

	private final EtiquetaService etiquetas;

	public AdminEtiquetaController(EtiquetaService etiquetas) {
		this.etiquetas = etiquetas;
	}

	@GetMapping
	@Operation(summary = "Lista etiquetas")
	public List<EtiquetaResponse> listar() {
		tiendaId();
		return etiquetas.listar();
	}

	@PostMapping
	@Operation(summary = "Crea una etiqueta")
	public EtiquetaResponse crear(@Valid @RequestBody CrearEtiquetaRequest req) {
		tiendaId();
		return etiquetas.crear(req);
	}

	@PostMapping("/{id}/productos")
	@Operation(summary = "Asigna la etiqueta a un producto propio")
	public void asignar(@PathVariable Long id, @Valid @RequestBody AsignarEtiquetaRequest req) {
		etiquetas.asignar(tiendaId(), id, req);
	}

	@DeleteMapping("/{id}/productos/{productoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Quita la etiqueta de un producto")
	public void quitar(@PathVariable Long id, @PathVariable Long productoId) {
		etiquetas.quitar(tiendaId(), id, productoId);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
