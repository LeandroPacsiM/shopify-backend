# ADR-012: Contacto y notas del pedido (V17)

## Estado

Aceptado.

## Fecha

2026-11-08.

## Contexto

El pedido no guardaba cómo contactar al comprador ni sus notas
(propuesta del diagrama 00). Sin esto, ante un problema de entrega la
tienda no tiene email/teléfono del pedido ni instrucciones del cliente.

## Decisión

Migración `V17__pedido_contacto`: `email_contacto`, `telefono_contacto`,
`notas_cliente` (nullable) en `checkout_sesiones` y en `pedidos`. El
checkout los recibe opcionales (email validado si viene, notas máx.
1000); al confirmar se copian al pedido como snapshot, igual que
totales y direcciones. Se exponen en `CheckoutResponse` y en el
`PedidoResponse` de confirmar; los listados existentes no cambian.

## Alternativas consideradas

- Solo en pedidos (pedirlos al confirmar): rechazado; el contacto se
  recoge durante el checkout, no después.
- Obligatorios: rechazado; rompería pedidos viejos y fricciona la compra.

## Consecuencias

### Positivas

- La tienda contacta al comprador y lee sus notas desde el pedido.

### Negativas

- PII adicional por pedido (GDPR: se anonimiza con el resto, nunca se borra).
