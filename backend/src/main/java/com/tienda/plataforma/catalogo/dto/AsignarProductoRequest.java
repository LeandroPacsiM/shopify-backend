package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AsignarProductoRequest(@NotNull Long productoId, @Min(0) Integer posicion) {
}
