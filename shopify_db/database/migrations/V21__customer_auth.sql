-- ==========================================================
-- MIGRACIÓN V21: auth CUSTOMER + roles staff (Alternativa B, ADR-016)
-- F1 del plan v5. Solo aditiva; sin backfill de owners (D5: confirma
-- SUPER_ADMIN en F3). Incluye:
--   1. CHECK rol_tienda + 'store_owner' (puerta: sin valores fuera de
--      los actuales) + índice único parcial ≤1 owner
--   2. cliente_sesiones (refresh rotado 7 días, patrón sesiones/V8)
--   3. cliente_codigos (HMAC 6 dígitos, TTL 10 min, intentos)
--   4. cliente_limite_ip (rate limit atómico por IP y ventana)
--   5. registros_auditoria.cliente_id (actor CUSTOMER)
--   6. fn_lock_tienda_owner(): llave compartida de advisory locks
--      para servicios (F3) y trigger V22b (condición 1 de ejecución)
-- No editar después de aplicarlo.
-- ==========================================================

-- ---------- 1. roles staff ----------
DO $$
DECLARE n BIGINT;
BEGIN
  SELECT COUNT(*) INTO n FROM tienda_usuarios
   WHERE rol_tienda NOT IN ('admin', 'operador');
  IF n > 0 THEN
    RAISE EXCEPTION 'V21: % filas tienda_usuarios con rol fuera de (admin,operador)', n;
  END IF;
END $$;

ALTER TABLE tienda_usuarios DROP CONSTRAINT chk_tienda_rol;
ALTER TABLE tienda_usuarios
  ADD CONSTRAINT chk_tienda_rol CHECK (rol_tienda IN ('admin', 'operador', 'store_owner'));

CREATE UNIQUE INDEX uq_tienda_owner
  ON tienda_usuarios(tienda_id) WHERE rol_tienda = 'store_owner';

-- ---------- 2. sesiones CUSTOMER ----------
CREATE TABLE cliente_sesiones (
  id BIGSERIAL PRIMARY KEY,
  cliente_id BIGINT NOT NULL REFERENCES clientes(id) ON DELETE RESTRICT,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  familia_id UUID,
  expira_en TIMESTAMPTZ NOT NULL,
  revocado_en TIMESTAMPTZ,
  reemplazado_por_id BIGINT REFERENCES cliente_sesiones(id) ON DELETE SET NULL,
  motivo_revocacion VARCHAR(50),
  ip VARCHAR(45),
  user_agent VARCHAR(255),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cliente_sesiones_cliente ON cliente_sesiones(cliente_id);
CREATE INDEX idx_cliente_sesiones_familia ON cliente_sesiones(familia_id);

-- ---------- 3. códigos CUSTOMER (el perfil nace al verificar, D3) ----------
CREATE TABLE cliente_codigos (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE RESTRICT,
  email VARCHAR(255) NOT NULL,
  cliente_id BIGINT REFERENCES clientes(id) ON DELETE CASCADE,
  codigo_hmac VARCHAR(255) NOT NULL,
  intentos INTEGER NOT NULL DEFAULT 0 CHECK (intentos >= 0),
  expira_en TIMESTAMPTZ NOT NULL,
  usado_en TIMESTAMPTZ,
  ip VARCHAR(45),
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cliente_codigos_tienda_email ON cliente_codigos(tienda_id, email);

-- ---------- 4. rate limit por IP (ventana horaria, atómico por fila) ----------
CREATE TABLE cliente_limite_ip (
  ip INET NOT NULL,
  ventana TIMESTAMPTZ NOT NULL,
  contador INTEGER NOT NULL DEFAULT 1 CHECK (contador >= 0),
  CONSTRAINT pk_cliente_limite_ip PRIMARY KEY (ip, ventana)
);

-- ---------- 5. actor CUSTOMER en auditoría ----------
ALTER TABLE registros_auditoria
  ADD COLUMN cliente_id BIGINT REFERENCES clientes(id) ON DELETE SET NULL;
CREATE INDEX idx_auditoria_cliente ON registros_auditoria(cliente_id);

-- ---------- 6. llave compartida de advisory locks ----------
CREATE OR REPLACE FUNCTION fn_lock_tienda_owner(p_tienda_id BIGINT)
RETURNS VOID AS $$
BEGIN
  PERFORM pg_advisory_xact_lock(hashtext('tienda_owner:' || p_tienda_id::TEXT));
END;
$$ LANGUAGE plpgsql;
