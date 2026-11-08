-- ==========================================================
-- MIGRACIÓN V14: descuento del checkout (consumo atómico al confirmar)
-- El descuento se valida al crear el checkout pero se registra y
-- consume al confirmar el pedido (evita gastar usos en abandonos).
-- No editar después de aplicarlo.
-- ==========================================================

ALTER TABLE checkout_sesiones
  ADD COLUMN descuento_id BIGINT NULL REFERENCES descuentos(id) ON DELETE SET NULL;
