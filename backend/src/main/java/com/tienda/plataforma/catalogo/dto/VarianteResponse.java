package com.tienda.plataforma.catalogo.dto;

import java.math.BigDecimal;

public record VarianteResponse(Long id, String sku, BigDecimal precio, boolean activo) {

	public static VarianteResponse de(com.tienda.plataforma.catalogo.entity.ProductoVariante v) {
		return new VarianteResponse(v.getId(), v.getSku(), v.getPrecio(), Boolean.TRUE.equals(v.getActivo()));
	}
}
