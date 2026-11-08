-- ==========================================================
-- MIGRACIÓN V4: no borrar historia (cancelar/archivar + FK seguras)
-- Basado en doc oficial Shopify (cancel/archive, productDelete,
-- locations): los pedidos se cancelan y archivan, no se borran;
-- el borrado de producto no arrastra pedidos/reembolsos;
-- la ubicación se desactiva antes de eliminarse.
-- No editar después de aplicarlo (crear V5 para lo siguiente).
-- ==========================================================

-- ---------- 1. pedidos: archivado lógico ----------
-- Cancelar ya existe (estado='cancelado'). Archivar = ocultar de la
-- lista de abiertos sin borrar. El backend debe prohibir DELETE de
-- pedidos salvo los 5 casos Shopify (prueba, pago manual, borrador,
-- importado por API); lo normal es cancelar + archivar.

ALTER TABLE pedidos
  ADD COLUMN archivado    BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN archivado_en TIMESTAMPTZ NULL;

ALTER TABLE pedidos
  ADD CONSTRAINT chk_archivado_coherente CHECK (
    (archivado = FALSE) OR (archivado_en IS NOT NULL)
  );

-- ---------- 2. resenas: borrar producto no borra reseñas ----------
-- Shopify preserva pedidos/reembolsos al borrar producto y no arrastra
-- reseñas en cascada. SET NULL + columna nullable.

ALTER TABLE resenas
  ALTER COLUMN producto_id DROP NOT NULL;

DO $$
DECLARE fk_name TEXT;
BEGIN
  SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid
     AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'resenas'::regclass
     AND c.contype = 'f'
     AND a.attname = 'producto_id';
  IF fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE resenas DROP CONSTRAINT %I', fk_name);
  END IF;
END $$;

ALTER TABLE resenas
  ADD CONSTRAINT fk_resenas_producto
  FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE SET NULL;

-- ---------- 3. niveles: no eliminar ubicación con stock ----------
-- Shopify: desactivar primero (ubicaciones.activo ya existe), eliminar
-- solo después. RESTRICT impide el DELETE mientras haya niveles.
-- El historial (pedidos, movimientos) se preserva.

DO $$
DECLARE fk_name TEXT;
BEGIN
  SELECT c.conname INTO fk_name
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid
     AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'niveles_inventario'::regclass
     AND c.contype = 'f'
     AND a.attname = 'ubicacion_id';
  IF fk_name IS NOT NULL THEN
    EXECUTE format('ALTER TABLE niveles_inventario DROP CONSTRAINT %I', fk_name);
  END IF;
END $$;

ALTER TABLE niveles_inventario
  ADD CONSTRAINT fk_niveles_ubicacion
  FOREIGN KEY (ubicacion_id) REFERENCES ubicaciones(id) ON DELETE RESTRICT;

-- NOTA: articulos_inventario.variante_id CASCADE se conserva a propósito:
-- borrar producto/variante sí elimina sus fichas de inventario
-- (igual que Shopify), pero nunca pedidos, pagos ni movimientos.
