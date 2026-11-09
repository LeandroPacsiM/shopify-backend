# ADR-015: Descuentos combinados en checkout (V19)

## Estado

Aceptado.

## Fecha

2026-11-09.

## Contexto

El checkout aceptaba un solo código (`checkout_sesiones.descuento_id`,
V14) mientras RF-DISC-01 permite combinar códigos distintos (máx. 5 de
producto por pedido + 1 de envío). El pedido ya soportaba multidescuento
(`pedido_descuentos`, V6), pero el checkout no tenía dónde conservar los
códigos ni sus montos calculados antes de confirmar.

## Decisión

Migración `V19__checkout_descuentos`: `checkout_descuentos`
(`checkout_sesion_id` CASCADE, `descuento_id` RESTRICT,
`monto_aplicado >= 0`, `UNIQUE (checkout_sesion_id, descuento_id)`) +
índices por checkout y descuento. El request acepta
`codigosDescuento` (lista) además del `codigoDescuento` singular
heredado; ambos se fusionan sin duplicados. Reglas en creación:
un código una vez, hasta 5 de producto + 1 de envío, cada uno con su
vigencia/alcance; la suma de producto se topa al subtotal y el envío
gratis deja el envío en cero. Al confirmar, cada fila se traslada a
`pedido_descuentos` con bloqueo y consumo atómico de `usos_actuales`
por código (los checkouts viejos con `descuento_id` único siguen
convirtiéndose por la vía anterior). La columna heredada se conserva
nullable por compatibilidad pero ya no se escribe.

## Alternativas consideradas

- Guardar los códigos como texto en `checkout_sesiones`: rechazado;
  pierde montos calculados, FKs y unicidad.
- Recalcular todo al confirmar sin tabla intermedia: rechazado; el
  checkout debe congelar importes y el uso se valida dos veces
  (al crear y al confirmar, como el stock).

## Consecuencias

- Un reintento con el mismo código no duplica (deduplicación) ni falla.
- La cancelación revierte cada uso vía `pedido_descuentos`, sin cambios.
- Verificado en `DescuentoCombinadoTest` (combinación, topes, usos) y
  en los tests heredados de un solo código (`CuponUltimoUsoTest`,
  `PedidoContactoTest`).
