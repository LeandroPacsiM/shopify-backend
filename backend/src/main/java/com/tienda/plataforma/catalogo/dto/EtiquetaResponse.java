package com.tienda.plataforma.catalogo.dto;

public record EtiquetaResponse(Long id, String nombre, String slug) {

	public static EtiquetaResponse de(com.tienda.plataforma.catalogo.entity.Etiqueta e) {
		return new EtiquetaResponse(e.getId(), e.getNombre(), e.getSlug());
	}
}
