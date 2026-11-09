package com.tienda.plataforma.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record AsignarEtiquetaRequest(@NotNull Long productoId) {
}
