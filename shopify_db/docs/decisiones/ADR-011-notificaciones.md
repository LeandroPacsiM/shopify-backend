# ADR-011: Notificaciones persistentes (V16, RF-ADMIN-05)

## Estado

Aceptado.

## Fecha

2026-11-08.

## Contexto

La campana WS avisa en vivo pero es efímera: admin offline = evento
perdido. Shopify guarda el timeline como log en BD y notifica al staff
por canales separados. Los 4 flujos ya llamaban a `notificar()` sin
dejar rastro.

## Decisión

Migración `V16__notificaciones`: `notificaciones` (`tienda_id` RESTRICT
como V11, `tipo` sin CHECK, referencia lógica `entidad/entidad_id`,
`datos` JSONB sin secretos, `leida_en`). `NotificacionService.notificar`
persiste en la TX del llamante (como la auditoría) y además emite por WS
con el `id` de la fila. Bandeja por tienda (admin/operador comparten
inbox): `GET /api/admin/notificaciones`, `GET .../no-leidas`,
`PATCH .../{id}/leer`; aviso ajeno = 404. Tipos unificados en
constantes (se suman `pedido_cancelado`, `pedido_entregado` a los 3
existentes).

## Alternativas consideradas

- Solo WS sin persistir: rechazado; pierde eventos offline.
- Solo BD con polling (sin WS): rechazado; la campana en vivo ya
  funciona y el diagrama 09 la define.
- Bandeja por usuario: rechazado; el staff rota, el aviso es de la tienda.

## Consecuencias

### Positivas

- Ningún evento se pierde; badge y bandeja leen la misma fuente.
- Mismo aislamiento por tienda que pedidos (tests anti-fuga).

### Negativas

- Sin purga de eventos (`pago_eventos`, `suscripcion_eventos` son dedup
  e historial: borrarlos reabriría efectos duplicados); la bandeja
  purga leídas hace +90d los domingos.
