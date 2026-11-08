package com.tienda.plataforma.venta.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AgregarLineaRequest(@NotNull Long varianteId, @NotNull @Min(1) Integer cantidad) {
}
