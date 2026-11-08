# ADR-007: Endurecimiento auth e integridad (migraciones V2–V7)

## Estado

Aceptado.

## Fecha

2026-11-07.

## Contexto

La línea base V1 delegaba a la aplicación reglas que la BD puede imponer (2FA sin columnas, webhooks sin idempotencia, unicidades de negocio ausentes, borrados que destruyen historia, `actualizado_en` manual) y permitía guest checkout, en conflicto con el requisito de 2FA para todos. Se investigó cada regla en la doc oficial de Shopify con subagentes antes de migrar.

## Decisión

Aplicar `database/migrations/V2–V7`:
- V2: columnas TOTP/bloqueo en `usuarios` + `usuario_codigos_recuperacion`.
- V3: idempotencia de pagos (`UNIQUE` por pasarela/pago/evento).
- V4: archivado lógico en `pedidos`; reseñas `SET NULL`; ubicaciones `RESTRICT`.
- V5: cliente obligatorio (`clientes`, `pedidos`, `checkout_sesiones` NOT NULL + `RESTRICT`); carrito anónimo permitido.
- V6: unicidades de negocio (dirección por tipo, descuento por pedido, reseña por cliente-producto, línea por envío).
- V7: triggers `actualizado_en`; auditoría escrita por el backend en la misma transacción.

Pendiente (candidata V8): `sesiones.token_hash` + rotación, `tokens_cuenta`, `notificaciones`, `tasa_impuesto_id`.

## Alternativas consideradas

- Dejarlo todo en aplicación: rechazado; los reintentos y la concurrencia (doble checkout, webhooks duplicados) necesitan constraints.
- Triggers de auditoría: rechazado; riesgo de recursión, el backend la escribe con actor.

## Consecuencias

### Positivas

- La BD rechaza duplicados, huérfanos y borrados destructivos aunque el backend falle.
- Réplica verificada del comportamiento Shopify (require-sign-in, noDELETE con historial, updatedAt automático).

### Negativas

- V5/V6 fallan si hay datos viejos con nulos o duplicados: exigen limpieza previa (queries incluidas en cada migración).
- `02-modelo-datos.md`, `03-diccionario` y `06-seguridad` deben mantenerse al día en la misma revisión.
