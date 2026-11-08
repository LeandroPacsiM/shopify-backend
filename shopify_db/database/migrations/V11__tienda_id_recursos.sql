-- ==========================================================
-- MIGRACIÓN V11: tienda_id en recursos (aislamiento multi-tenant)
-- Tarea 2 del plan. Raíces con tienda propia: productos, clientes,
-- pedidos, ubicaciones, descuentos, carritos, checkout_sesiones,
-- metodos_envio, tasas_impuesto. El resto la hereda por join
-- (envíos/pagos → pedido; reseñas → producto/cliente; niveles →
-- ubicación) para no tener doble fuente de verdad.
-- No editar después de aplicarlo.
-- ==========================================================

-- ---------- 1. tienda demo para el backfill ----------
INSERT INTO tiendas (nombre, slug, descripcion)
VALUES ('Tienda Demo', 'tienda-demo', 'Tenant inicial para datos pre multi-tenant')
ON CONFLICT (slug) DO NOTHING;

-- ---------- 2. columnas (nullable para poder rellenar) ----------
ALTER TABLE productos          ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE clientes           ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE pedidos            ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE ubicaciones        ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE descuentos         ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE carritos           ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE checkout_sesiones  ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE metodos_envio      ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;
ALTER TABLE tasas_impuesto     ADD COLUMN tienda_id BIGINT NULL REFERENCES tiendas(id) ON DELETE RESTRICT;

-- ---------- 3. backfill: todo lo existente cae en la demo ----------
UPDATE productos         SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE clientes          SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE pedidos           SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE ubicaciones       SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE descuentos        SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE carritos          SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE checkout_sesiones SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE metodos_envio     SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;
UPDATE tasas_impuesto    SET tienda_id = (SELECT id FROM tiendas WHERE slug = 'tienda-demo') WHERE tienda_id IS NULL;

-- ---------- 4. obligatorias + índices ----------
ALTER TABLE productos          ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE clientes           ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE pedidos            ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE ubicaciones        ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE descuentos         ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE carritos           ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE checkout_sesiones  ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE metodos_envio      ALTER COLUMN tienda_id SET NOT NULL;
ALTER TABLE tasas_impuesto     ALTER COLUMN tienda_id SET NOT NULL;

CREATE INDEX idx_productos_tienda         ON productos(tienda_id);
CREATE INDEX idx_clientes_tienda          ON clientes(tienda_id);
CREATE INDEX idx_pedidos_tienda           ON pedidos(tienda_id);
CREATE INDEX idx_ubicaciones_tienda       ON ubicaciones(tienda_id);
CREATE INDEX idx_descuentos_tienda        ON descuentos(tienda_id);
CREATE INDEX idx_carritos_tienda          ON carritos(tienda_id);
CREATE INDEX idx_checkout_tienda          ON checkout_sesiones(tienda_id);
CREATE INDEX idx_metodos_envio_tienda     ON metodos_envio(tienda_id);
CREATE INDEX idx_tasas_impuesto_tienda    ON tasas_impuesto(tienda_id);

-- NOTAS:
-- - ON DELETE RESTRICT: no se borra una tienda con recursos (tarea 3
--   define la baja: desactivar con tiendas.activa = FALSE).
-- - Coherencia cruzada (p. ej. pedido y cliente de la misma tienda)
--   se valida en backend (tarea 7): un CHECK no cruza tablas.
