-- ==========================================================
-- MIGRACIÓN V2: 2FA TOTP + códigos de recuperación + bloqueo
-- Motor: PostgreSQL (dev local ahora; Supabase en prod)
-- Convenciones heredadas de V1 (01_esquema_shopify.sql):
--   BIGSERIAL PK, BIGINT FK, TIMESTAMPTZ, constraints uq_*
-- Flyway la ejecuta una sola vez; no editar este archivo
-- después de aplicarlo (crear V3 para lo siguiente).
-- ==========================================================

-- ---------- 1. usuarios: columnas TOTP + verificación + bloqueo ----------

ALTER TABLE usuarios
  ADD COLUMN email_verificado   BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN totp_secret        TEXT        NULL,
  ADD COLUMN totp_habilitado    BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN totp_confirmado_en TIMESTAMPTZ NULL,
  ADD COLUMN intentos_fallidos  INTEGER     NOT NULL DEFAULT 0,
  ADD COLUMN bloqueado_hasta    TIMESTAMPTZ NULL,
  ADD COLUMN ultimo_acceso      TIMESTAMPTZ NULL;

-- NOTAS:
-- - totp_secret guarda el secreto CIFRADO (AES-GCM, llave en variable
--   de entorno). Jamás en claro ni en logs.
-- - Flujo alta: generar secreto (habilitado=FALSE) -> mostrar QR
--   (otpauth://) -> verificar 1er código -> habilitado=TRUE
--   + totp_confirmado_en=NOW() en la misma transacción.
-- - Filas existentes quedan con habilitado=FALSE: el CHECK pasa.

ALTER TABLE usuarios
  ADD CONSTRAINT chk_totp_coherente CHECK (
    (totp_habilitado = FALSE)
    OR (totp_secret IS NOT NULL AND totp_confirmado_en IS NOT NULL)
  );

ALTER TABLE usuarios
  ADD CONSTRAINT chk_intentos_no_neg CHECK (intentos_fallidos >= 0);

-- ---------- 2. códigos de recuperación (backup codes, un solo uso) ----------

CREATE TABLE usuario_codigos_recuperacion (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  codigo_hash VARCHAR(255) NOT NULL,
  usado_en TIMESTAMPTZ NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_usuario_codigo UNIQUE (usuario_id, codigo_hash)
);

-- NOTAS:
-- - codigo_hash con BCrypt/Argon2 (igual que passwords). El código
--   original se muestra UNA vez y no se vuelve a consultar.
-- - Al usar un código: usado_en=NOW() (no se borra, queda auditoría).
-- - Regenerar lote invalida el anterior (marcar usados en transacción).

CREATE INDEX idx_codigos_recuperacion_usuario
  ON usuario_codigos_recuperacion(usuario_id);

-- ---------- Pendiente para V3 (no tocar aquí) ----------
-- - sesiones.token -> token_hash + familia_id/revocación (refresh rotado).
-- - tokens_cuenta (verificación email / reset password).
-- - notificaciones (eventos usuario/admin).
