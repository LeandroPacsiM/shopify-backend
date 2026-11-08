# ADR-008: Sesiones rotadas, snapshot de impuestos y mock de pagos (V8–V9)

## Estado

Aceptado.

## Fecha

2026-11-07.

## Contexto

Quedaban del grupo B: tokens en claro, `tasas_impuesto` sin histórico y pasarelas sin diseño. Se investigó cada punto en la doc oficial de Shopify con un subagente.

## Decisión

- V8: `sesiones.token_hash` (SHA-256) + `familia_id`/`revocado_en`/`reemplazado_por_id`; `token` pasa a `token_legacy` hasta el cutover. Reuso de refresh rotado = revocar la familia (patrón OAuth de Shopify).
- V9: `pedido_impuestos` como snapshot por pedido/línea (título, tasa, monto); `tasas_impuesto` queda solo para cotizar, como las tax lines de Shopify.
- Pagos: interfaz `PaymentAdapter` con mock (`test=TRUE`) primero; Stripe test después. Sin cambios DDL: V3 ya da la idempotencia.
- Backend pendiente: endpoint admin de reseteo 2FA y job de limpieza de sesiones.

## Alternativas consideradas

- Passwordless como Shopify: rechazado; el requisito es password + TOTP para todos.
- Enlazar el pedido a `tasas_impuesto` con FK: rechazado; el histórico debe congelarse, no recalcularse.

## Consecuencias

### Positivas

- Secretos y tokens nunca en claro; webhooks y reintentos idempotentes; impuestos auditables.

### Negativas

- El cutover de `token_legacy` exige una migración posterior con backfill y `SET NOT NULL`.
- `kind`/`parent_id` del adaptador real se añaden cuando llegue el primer proveedor.
