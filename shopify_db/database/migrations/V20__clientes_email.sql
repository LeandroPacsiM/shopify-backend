-- ==========================================================
-- MIGRACIÓN V20: clientes.email propio (Alternativa B, ADR-016)
-- F1 del plan v5. Solo aditiva + backfill; el código actual sigue
-- funcionando. Orden estricto:
--   1. ADD email nullable
--   2. backfill normalizado lower(trim()) desde usuarios (re-ejecutable)
--   3. PUERTA: sin NULLs (falla listando ofensores)
--   4. PUERTA: sin duplicados (tienda_id, email) tras normalizar
--   5. SET NOT NULL + UNIQUE(tienda_id, email)
--   6. usuario_id: DROP NOT NULL + DROP UNIQUE (conserva FK RESTRICT)
-- No editar después de aplicarlo.
-- ==========================================================

ALTER TABLE clientes ADD COLUMN email VARCHAR(255);

-- ---------- 2. backfill normalizado (re-ejecutable) ----------
UPDATE clientes c
SET email = NULLIF(lower(trim(u.email)), '')
FROM usuarios u
WHERE u.id = c.usuario_id AND c.email IS NULL;

-- ---------- 3. PUERTA: sin NULLs ----------
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT COUNT(*) INTO n FROM clientes WHERE email IS NULL;
  IF n > 0 THEN
    RAISE EXCEPTION 'V20: % clientes sin email (revisar usuarios.email origen)', n;
  END IF;
END $$;

-- ---------- 4. PUERTA: duplicados tras normalizar ----------
DO $$
DECLARE detalle TEXT;
BEGIN
  SELECT string_agg('tienda=' || tienda_id || ' email=' || email || ' x' || n, '; ')
    INTO detalle
  FROM (SELECT tienda_id, email, COUNT(*) AS n FROM clientes GROUP BY tienda_id, email
        HAVING COUNT(*) > 1) d;
  IF detalle IS NOT NULL THEN
    RAISE EXCEPTION 'V20: emails duplicados por tienda tras normalizar: %. Corregir usuarios.email origen y re-ejecutar.', detalle;
  END IF;
END $$;

-- ---------- 5. NOT NULL + unicidad por tienda ----------
ALTER TABLE clientes ALTER COLUMN email SET NOT NULL;
ALTER TABLE clientes
  ADD CONSTRAINT uq_clientes_tienda_email UNIQUE (tienda_id, email);
CREATE INDEX idx_clientes_email ON clientes(email);

-- ---------- 6. usuario_id: nullable transitorio (D: se elimina en V23) ----------
ALTER TABLE clientes ALTER COLUMN usuario_id DROP NOT NULL;

DO $$
DECLARE fk TEXT;
BEGIN
  SELECT c.conname INTO fk
    FROM pg_constraint c
    JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
   WHERE c.conrelid = 'clientes'::regclass
     AND c.contype = 'u'
     AND a.attname = 'usuario_id';
  IF fk IS NOT NULL THEN
    EXECUTE format('ALTER TABLE clientes DROP CONSTRAINT %I', fk);
  END IF;
END $$;

-- NOTA: idx_clientes_usuario se conserva (F3 lo usa en el cutover).
-- La FK fk_clientes_usuario (RESTRICT, V5) se conserva hasta V23.
