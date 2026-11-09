package com.tienda.plataforma.catalogo.dto;

import java.math.BigDecimal;

public record ProductoCardResponse(Long id, String titulo, String slug, BigDecimal precioDesde) {
}
