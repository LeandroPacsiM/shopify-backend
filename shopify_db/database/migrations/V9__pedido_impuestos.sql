-- ==========================================================
-- MIGRACIÓN V9: snapshot de impuestos por pedido (estilo Shopify)
-- Shopify no enlaza el pedido a una tabla de tasas: congela
-- tax_lines por pedido/línea (título, tasa, monto). tasas_impuesto
-- queda como catálogo vivo para cotizar, nunca como fuente histórica.
-- No editar después de aplicarlo (crear V10 para lo siguiente).
-- ==========================================================

CREATE TABLE pedido_impuestos (
  id BIGSERIAL PRIMARY KEY,
  pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
  pedido_item_id BIGINT NULL REFERENCES pedido_items(id) ON DELETE CASCADE,
  titulo VARCHAR(100) NOT NULL,
  tasa NUMERIC(7,4) NOT NULL CHECK (tasa >= 0),
  monto NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_pedido_impuesto UNIQUE (pedido_id, pedido_item_id, titulo)
);

-- NOTAS:
-- - pedido_item_id NULL = impuesto a nivel de envío/cabecera.
-- - Se copian título+tasa+monto al confirmar el pedido; jamás se
--   recalculan desde tasas_impuesto viva.
-- - El backend valida en la misma transacción que la suma de montos
--   = pedidos.impuesto_total (redondeo NUMERIC uniforme).

CREATE INDEX idx_pedido_impuestos_pedido ON pedido_impuestos(pedido_id);
