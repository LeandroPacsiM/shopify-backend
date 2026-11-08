-- ==========================================================
-- MIGRACIÓN V6: unicidades que Shopify sí impone
-- Verificado en doc oficial (Order.shipping/billing singulares,
-- discount-combinations, Shop product-reviews, fulfillments).
-- No editar después de aplicarlo (crear V7 para lo siguiente).
-- ==========================================================
-- ANTES DE APLICAR (solo si ya hay datos): deben dar 0 filas.
--
-- SELECT pedido_id, tipo, COUNT(*) FROM pedido_direcciones
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- SELECT pedido_id, descuento_id, COUNT(*) FROM pedido_descuentos
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- SELECT producto_id, cliente_id, COUNT(*) FROM resenas
--   WHERE cliente_id IS NOT NULL
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- SELECT envio_id, pedido_item_id, COUNT(*) FROM envio_items
--   GROUP BY 1, 2 HAVING COUNT(*) > 1;
-- ==========================================================

-- ---------- 1. direcciones: 1 envío + 1 facturación por pedido ----------
-- Order expone shipping_address y billing_address en singular.

ALTER TABLE pedido_direcciones
  ADD CONSTRAINT uq_pedido_direccion_tipo
  UNIQUE (pedido_id, tipo);

-- ---------- 2. descuentos: un código, una vez por pedido ----------
-- Mismo código no se aplica 2 veces (checkout: "already been used").
-- Códigos DISTINTOS sí combinan: máx 5 producto/pedido + 1 envío,
-- shipping+shipping nunca. Esos límites van en backend (V6 solo
-- impide la repetición; la matriz de combinación es regla de app).

ALTER TABLE pedido_descuentos
  ADD CONSTRAINT uq_pedido_descuento
  UNIQUE (pedido_id, descuento_id);

-- ---------- 3. reseñas: 1 por cliente y producto ----------
-- Shopify: 1 reseña por producto por compra (solo quien compró).
-- Simplificación v1: 1 por (producto, cliente). Si a futuro se quiere
-- "una por compra", añadir pedido_id a resenas y mover el UNIQUE.

ALTER TABLE resenas
  ADD CONSTRAINT uq_resena_producto_cliente
  UNIQUE (producto_id, cliente_id);

-- ---------- 4. envío_items: una línea, una vez por envío ----------
-- La misma línea SÍ puede repartirse en VARIOS envíos (cantidades
-- parciales: fulfillmentCreate recibe quantity por línea). Lo que se
-- impide aquí es duplicarla dentro del MISMO envío (reintentos).
-- La suma entre envíos <= cantidad de la línea se valida en backend
-- con bloqueo, igual que Shopify (fulfillable = qty - fulfilled...).

ALTER TABLE envio_items
  ADD CONSTRAINT uq_envio_pedido_item
  UNIQUE (envio_id, pedido_item_id);
