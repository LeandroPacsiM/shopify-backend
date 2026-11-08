-- ==========================================================
-- MIGRACIÓN V3: idempotencia de pagos (unicidades externas)
-- Motor: PostgreSQL (dev local ahora; Supabase en prod)
-- No editar después de aplicarlo (crear V4 para lo siguiente).
-- ==========================================================
-- ANTES DE APLICAR (solo si ya hay datos): detectar duplicados
-- que harían fallar los UNIQUE. Deben dar 0 filas:
--
-- SELECT pasarela_pago_id, transaccion_externa_id, COUNT(*)
--   FROM pagos WHERE transaccion_externa_id IS NOT NULL
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- SELECT pago_id, transaccion_externa_id, COUNT(*)
--   FROM reembolsos WHERE transaccion_externa_id IS NOT NULL
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- SELECT pago_id, referencia_externa, COUNT(*)
--   FROM pago_eventos WHERE referencia_externa IS NOT NULL
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- ==========================================================

-- ---------- 1. pagos: un id externo vale una vez por pasarela ----------
-- Permite: mismo id en pasarelas distintas (cada proveedor numera
-- a su manera) y varios intentos locales sin ref externa (NULL).
-- Bloquea: reintentar/webhook duplicado del mismo cobro.

ALTER TABLE pagos
  ADD CONSTRAINT uq_pago_pasarela_transaccion
  UNIQUE (pasarela_pago_id, transaccion_externa_id);

-- ---------- 2. reembolsos: un id externo vale una vez por pago ----------

ALTER TABLE reembolsos
  ADD CONSTRAINT uq_reembolso_pago_transaccion
  UNIQUE (pago_id, transaccion_externa_id);

-- ---------- 3. pago_eventos: dedup de webhooks por event-id ----------
-- referencia_externa debe guardar el ID DEL EVENTO del proveedor
-- (no el id de transacción). NULL permitido para eventos generados
-- localmente sin ref externa.

ALTER TABLE pago_eventos
  ADD CONSTRAINT uq_pago_evento_referencia
  UNIQUE (pago_id, referencia_externa);

-- ---------- Nota: moneda pedido/pago/reembolso (sin cambio DDL) ----------
-- Un CHECK no puede cruzar tablas, así que la igualdad de moneda
-- (pago.moneda = pedido.moneda, reembolso.moneda = pago.moneda)
-- queda como validación obligatoria del backend en cada operación
-- de pago/reembolso, dentro de la misma transacción. Ver AGENTS.md.
