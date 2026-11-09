package com.tienda.plataforma.catalogo.dto;

public record ColeccionResponse(Long id, String titulo, String slug, String descripcion, Boolean activo) {

	public static ColeccionResponse de(com.tienda.plataforma.catalogo.entity.Coleccion c) {
		return new ColeccionResponse(c.getId(), c.getTitulo(), c.getSlug(), c.getDescripcion(),
				c.getActivo());
	}
}
