-- ==========================================================
-- MIGRACIÓN V18: suscripción de plataforma por tienda (RF-BILL)
-- La tienda paga por usar el software (plan mensual/anual con NUESTRAS
-- llaves); lo que cobra a sus clientes va por SU pasarela (V10).
-- Una fila por tienda (UNIQUE). Impago = tienda congelada
-- (tiendas.activa=FALSE), nunca DELETE con historial.
-- El cobro Stripe (Subscriptions API) llega en V19; aquí el modelo,
-- la máquina de estados y el enforcement (sin suscripción activa no
-- hay checkout nuevo). Webhooks con dedup por referencia externa.
-- No editar después de aplicarlo.
-- ==========================================================

CREATE TABLE suscripciones (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL UNIQUE REFERENCES tiendas(id) ON DELETE RESTRICT,
  plan VARCHAR(20) NOT NULL,
  estado VARCHAR(20) NOT NULL DEFAULT 'pendiente',
  stripe_customer_id VARCHAR(255) NULL,
  stripe_subscription_id VARCHAR(255) NULL UNIQUE,
  periodo_fin TIMESTAMPTZ NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_suscripcion_plan CHECK (plan IN ('mensual', 'anual')),
  CONSTRAINT chk_suscripcion_estado CHECK (estado IN ('pendiente', 'activa', 'vencida', 'cancelada'))
);

-- NOTAS:
-- - pendiente: creada, a la espera del primer pago (webhook la activa).
-- - periodo_fin lo fija el webhook de pago: +30 días (mensual) o
--   +365 (anual) desde el pago. Vencida/cancelada sin periodo futuro
--   bloquea checkout nuevo (tienda congelada, como Shopify).
-- - UNIQUE en stripe_subscription_id admite varios NULL (PG): varias
--   pendientes sin id de Stripe conviven; al asignar id debe ser único.

CREATE TABLE suscripcion_eventos (
  id BIGSERIAL PRIMARY KEY,
  suscripcion_id BIGINT NOT NULL REFERENCES suscripciones(id) ON DELETE CASCADE,
  referencia_externa VARCHAR(255) NOT NULL UNIQUE,
  tipo VARCHAR(100) NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_suscripcion_eventos_suscripcion
  ON suscripcion_eventos(suscripcion_id);

DROP TRIGGER IF EXISTS trg_suscripciones_actualizado_en ON suscripciones;
CREATE TRIGGER trg_suscripciones_actualizado_en
  BEFORE UPDATE ON suscripciones
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();
