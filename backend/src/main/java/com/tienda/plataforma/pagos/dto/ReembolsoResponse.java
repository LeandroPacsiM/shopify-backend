package com.tienda.plataforma.pagos.dto;

import java.math.BigDecimal;

public record ReembolsoResponse(Long id, String estado, BigDecimal monto, String transaccionExternaId) {
}
