package com.tienda.plataforma.catalogo.dto;

import java.util.List;

public record ProductoDetalleResponse(Long id, String titulo, String slug, String descripcion,
		List<VarianteResponse> variantes, List<String> etiquetas) {
}
