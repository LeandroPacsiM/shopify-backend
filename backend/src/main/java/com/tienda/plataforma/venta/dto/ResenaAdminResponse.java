package com.tienda.plataforma.venta.dto;

public record ResenaAdminResponse(Long id, Long productoId, String productoTitulo, Integer calificacion,
		String titulo, String comentario, Boolean aprobada) {
}
