package com.tienda.plataforma.envios.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CrearEnvioRequest(
		Long transportistaId,
		Long ubicacionOrigenId,
		@NotEmpty List<@Valid ItemEnvio> items) {

	public record ItemEnvio(@NotNull Long pedidoItemId, @NotNull @Min(1) Integer cantidad) {
	}
}
