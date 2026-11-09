package com.tienda.plataforma.venta.dto;

public record ResenaPropiaResponse(Long id, Long productoId, Integer calificacion, String titulo,
		String comentario, Boolean aprobada) {
}
