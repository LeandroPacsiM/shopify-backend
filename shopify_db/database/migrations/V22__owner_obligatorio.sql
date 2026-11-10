-- ==========================================================
-- MIGRACIÓN V22: obligatoriedad del STORE_OWNER (Alternativa B, ADR-016)
-- F4a del plan v5 (ajuste 1 del v5 + condición 1 de ejecución).
--   V22a: como máximo un owner → ya impuesto por `uq_tienda_owner`
--     (V21); aquí se verifica como puerta dura.
--   V22b: al menos un owner → CONSTRAINT TRIGGERs diferidos (se
--     evalúan al commit, así la transferencia atómica en una TX pasa):
--     - `trg_tienda_usuarios_owner`: prohíbe reasignar tienda_id y
--       prohíbe dejar una tienda sin owner al degradar/revocar.
--       Solo actúa cuando la fila afectada ERA owner; las tiendas
--       legacy sin owner (pendientes de confirmar en F3) no fallan
--       salvo que se toque a su (inexistente) owner.
--     - `trg_tiendas_owner`: toda tienda creada debe tener owner al
--       commit (el onboarding crea tienda+owner en la misma TX).
--   Concurrencia: ambas funciones bloquean con `fn_lock_tienda_owner()`,
--   la misma llave de los servicios (F3), así que mutaciones paralelas
--   se serializan por tienda.
-- Puertas: falla si alguna tienda tiene >1 owner; las tiendas con 0
-- owners solo se AVISAN (NOTICE) para el proceso de confirmación F3.
-- No editar después de aplicarlo.
-- ==========================================================

-- ---------- V22a: puerta dura ≤1 owner ----------
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT COUNT(*) INTO n FROM (
    SELECT tienda_id FROM tienda_usuarios
    WHERE rol_tienda = 'store_owner'
    GROUP BY tienda_id HAVING COUNT(*) > 1) d;
  IF n > 0 THEN
    RAISE EXCEPTION 'V22: % tiendas con más de un store_owner', n;
  END IF;
END $$;

-- ---------- V22b: funciones ----------
CREATE OR REPLACE FUNCTION chk_tienda_usuarios_owner()
RETURNS TRIGGER AS $$
DECLARE n_owners BIGINT;
BEGIN
  -- La membresía no se mueve entre tiendas.
  IF TG_OP = 'UPDATE' AND OLD.tienda_id IS DISTINCT FROM NEW.tienda_id THEN
    RAISE EXCEPTION 'tienda_usuarios.tienda_id es inmutable';
  END IF;
  -- Solo importa perder un owner (INSERT nunca deja en 0).
  IF TG_OP IN ('DELETE', 'UPDATE') AND OLD.rol_tienda = 'store_owner' THEN
    PERFORM fn_lock_tienda_owner(OLD.tienda_id);
    SELECT COUNT(*) INTO n_owners FROM tienda_usuarios
     WHERE tienda_id = OLD.tienda_id AND rol_tienda = 'store_owner' AND id <> OLD.id;
    IF n_owners = 0 THEN
      RAISE EXCEPTION 'la tienda % quedaría sin store_owner (transfiere primero)', OLD.tienda_id;
    END IF;
  END IF;
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE' THEN
    RETURN NEW;
  END IF;
  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION chk_tiendas_owner()
RETURNS TRIGGER AS $$
DECLARE n_owners BIGINT;
BEGIN
  PERFORM fn_lock_tienda_owner(NEW.id);
  SELECT COUNT(*) INTO n_owners FROM tienda_usuarios
   WHERE tienda_id = NEW.id AND rol_tienda = 'store_owner';
  IF n_owners = 0 THEN
    RAISE EXCEPTION 'la tienda % (%) queda sin store_owner', NEW.id, NEW.slug;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_tienda_usuarios_owner ON tienda_usuarios;
CREATE CONSTRAINT TRIGGER trg_tienda_usuarios_owner
  AFTER INSERT OR UPDATE OR DELETE ON tienda_usuarios
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION chk_tienda_usuarios_owner();

DROP TRIGGER IF EXISTS trg_tiendas_owner ON tiendas;
CREATE CONSTRAINT TRIGGER trg_tiendas_owner
  AFTER INSERT ON tiendas
  DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION chk_tiendas_owner();

-- ---------- Aviso (no falla): tiendas pendientes de confirmar ----------
DO $$
DECLARE detalle TEXT;
BEGIN
  SELECT string_agg(slug, ', ') INTO detalle FROM tiendas t
   WHERE NOT EXISTS (SELECT 1 FROM tienda_usuarios tu
    WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'store_owner');
  IF detalle IS NOT NULL THEN
    RAISE NOTICE 'V22: tiendas sin owner pendiente de confirmar (F3): %', detalle;
  END IF;
END $$;
