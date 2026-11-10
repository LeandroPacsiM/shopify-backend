package com.tienda.plataforma.shared.security;

/**
 * Principal del ámbito compras (objetivo B, ADR-016). Nunca es un `Long`:
 * los managers admin solo aceptan `Long` y `clienteDeTienda` solo acepta
 * este tipo, así los JWT de ambos mundos no se intercambian.
 */
public record ClientePrincipal(Long clienteId, Long tiendaId) {
}
