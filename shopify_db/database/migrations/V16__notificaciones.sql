-- ==========================================================
-- MIGRACIÓN V16: notificaciones persistentes (bandeja admin)
-- La campana WS es efímera: si el admin está offline pierde el evento.
-- Esta tabla es la fuente (el WS solo avisa); la bandeja se lee por API.
-- Bandeja por tienda (admin/operador comparten inbox de su tienda).
-- `datos` JSONB sin secretos ni PII: solo ids/número/total.
-- No editar después de aplicarlo.
-- ==========================================================

CREATE TABLE notificaciones (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE RESTRICT,
  tipo VARCHAR(50) NOT NULL,
  entidad VARCHAR(100) NULL,
  entidad_id BIGINT NULL,
  datos JSONB NULL,
  leida_en TIMESTAMPTZ NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- NOTAS:
-- - entidad/entidad_id es referencia lógica (p. ej. pedido/123),
--   sin FK: igual que registros_auditoria (referencia polimórfica).
-- - Sin CHECK en tipo: los eventos crecen (nuevo_pedido, pedido_pagado,
--   pedido_cancelado, pedido_entregado, stock_bajo...); los válidos los
--   define NotificacionService.
-- - ON DELETE RESTRICT como el resto de recursos con tienda_id (V11):
--   la baja es desactivar la tienda.

CREATE INDEX idx_notificaciones_tienda
  ON notificaciones(tienda_id);
