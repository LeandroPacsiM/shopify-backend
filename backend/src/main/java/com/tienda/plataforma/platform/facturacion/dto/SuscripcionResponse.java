package com.tienda.plataforma.platform.facturacion.dto;

import java.time.Instant;

public record SuscripcionResponse(String plan, String estado, Instant periodoFin) {
}
