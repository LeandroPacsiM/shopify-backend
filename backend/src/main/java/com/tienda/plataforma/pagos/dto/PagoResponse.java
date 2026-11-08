package com.tienda.plataforma.pagos.dto;

import java.math.BigDecimal;

public record PagoResponse(Long id, String estado, BigDecimal monto, String transaccionExternaId) {
}
