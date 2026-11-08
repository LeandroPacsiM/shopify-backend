-- ==========================================================
-- MIGRACIÓN V7: actualizado_en automático (estilo Shopify)
-- Shopify: updatedAt lo gestiona la plataforma (solo lectura) y la
-- auditoría (timeline/activity/events) se genera sola en cada cambio.
-- Réplica aquí: trigger que refresca actualizado_en (la "plataforma")
-- + registros_auditoria escrito por el backend en la misma
-- transacción que el cambio (nuestro "timeline automático").
-- No editar después de aplicarlo (crear V8 para lo siguiente).
-- ==========================================================

CREATE OR REPLACE FUNCTION trg_refrescar_actualizado_en()
RETURNS TRIGGER AS $$
BEGIN
  NEW.actualizado_en = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Solo las 5 tablas que ya traen la columna desde V1:
-- usuarios, productos, carritos, pedidos, niveles_inventario.

DROP TRIGGER IF EXISTS trg_usuarios_actualizado_en ON usuarios;
CREATE TRIGGER trg_usuarios_actualizado_en
  BEFORE UPDATE ON usuarios
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

DROP TRIGGER IF EXISTS trg_productos_actualizado_en ON productos;
CREATE TRIGGER trg_productos_actualizado_en
  BEFORE UPDATE ON productos
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

DROP TRIGGER IF EXISTS trg_carritos_actualizado_en ON carritos;
CREATE TRIGGER trg_carritos_actualizado_en
  BEFORE UPDATE ON carritos
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

DROP TRIGGER IF EXISTS trg_pedidos_actualizado_en ON pedidos;
CREATE TRIGGER trg_pedidos_actualizado_en
  BEFORE UPDATE ON pedidos
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

DROP TRIGGER IF EXISTS trg_niveles_actualizado_en ON niveles_inventario;
CREATE TRIGGER trg_niveles_actualizado_en
  BEFORE UPDATE ON niveles_inventario
  FOR EACH ROW EXECUTE FUNCTION trg_refrescar_actualizado_en();

-- NOTA auditoría (sin DDL): registros_auditoria NO lleva triggers
-- (evita recursión y basura automática). El backend la escribe
-- siempre en la misma transacción que el cambio, con actor, entidad,
-- datos_anteriores/nuevos mínimos y sin secretos. Es nuestro
-- equivalente al timeline que Shopify genera solo.
