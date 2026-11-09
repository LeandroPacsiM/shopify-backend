package com.tienda.plataforma.catalogo.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.tienda.plataforma.catalogo.dto.AsignarProductoRequest;
import com.tienda.plataforma.catalogo.dto.ColeccionResponse;
import com.tienda.plataforma.catalogo.dto.CrearColeccionRequest;
import com.tienda.plataforma.catalogo.dto.EditarColeccionRequest;
import com.tienda.plataforma.catalogo.service.ColeccionService;
import com.tienda.plataforma.shared.exception.NoAutorizadoException;
import com.tienda.plataforma.shared.security.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin de tienda: colecciones y asignación de productos propios (RF-ADMIN-01). */
@RestController
@RequestMapping("/api/admin/colecciones")
@Tag(name = "Admin colecciones", description = "Colecciones editoriales y sus productos")
public class AdminColeccionController {

	private final ColeccionService colecciones;

	public AdminColeccionController(ColeccionService colecciones) {
		this.colecciones = colecciones;
	}

	@GetMapping
	@Operation(summary = "Lista colecciones")
	public List<ColeccionResponse> listar() {
		tiendaId();
		return colecciones.listar();
	}

	@PostMapping
	@Operation(summary = "Crea una colección")
	public ColeccionResponse crear(@Valid @RequestBody CrearColeccionRequest req) {
		tiendaId();
		return colecciones.crear(req);
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Edita título, descripción o estado")
	public ColeccionResponse editar(@PathVariable Long id, @Valid @RequestBody EditarColeccionRequest req) {
		tiendaId();
		return colecciones.editar(id, req);
	}

	@PostMapping("/{id}/productos")
	@Operation(summary = "Asigna un producto propio a la colección")
	public void asignar(@PathVariable Long id, @Valid @RequestBody AsignarProductoRequest req) {
		colecciones.asignar(tiendaId(), id, req);
	}

	@DeleteMapping("/{id}/productos/{productoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Quita un producto de la colección")
	public void quitar(@PathVariable Long id, @PathVariable Long productoId) {
		colecciones.quitar(tiendaId(), id, productoId);
	}

	private Long tiendaId() {
		if (TenantContext.actual() == null) {
			throw new NoAutorizadoException("sin tienda");
		}
		return TenantContext.actual().getId();
	}
}
