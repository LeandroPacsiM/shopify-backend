package com.tienda.plataforma.inventario.dto;

public record NivelResponse(Long nivelId, Long varianteId, Long ubicacionId, Integer disponible,
		Integer reservado) {
}
