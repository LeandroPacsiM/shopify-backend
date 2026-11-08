package com.tienda.plataforma.catalogo.dto;

public record ProductoResponse(Long id, String titulo, String slug, String estado) {

	public static ProductoResponse de(com.tienda.plataforma.catalogo.entity.Producto p) {
		return new ProductoResponse(p.getId(), p.getTitulo(), p.getSlug(), p.getEstado());
	}
}
