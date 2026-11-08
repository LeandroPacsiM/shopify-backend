-- ==========================================================
-- MIGRACIÓN V8: sesiones seguras (hash + rotación + revocación)
-- Replica Shopify (OAuth + refresh rotado): access corto en memoria,
-- refresh opaco rotado en BD. Reusar un refresh viejo = posible robo:
-- se revoca la familia entera.
-- No editar después de aplicarlo (crear V10 para lo siguiente).
-- ==========================================================

-- ---------- 1. nuevo modelo de token ----------
-- token_hash: SHA-256 del refresh opaco. El valor original solo lo
-- conoce el cliente (cookie HttpOnly + Secure + SameSite=Lax).
-- familia_id: agrupa las rotaciones de una misma cadena de login.
-- reemplazado_por_id: cadena de rotación (auditable).
-- La columna legacy se conserva para transición y se elimina cuando
-- el backend complete el cutover (ver nota al final).

ALTER TABLE sesiones
  ADD COLUMN token_hash         VARCHAR(255) NULL UNIQUE,
  ADD COLUMN familia_id         UUID         NULL,
  ADD COLUMN revocado_en        TIMESTAMPTZ  NULL,
  ADD COLUMN reemplazado_por_id BIGINT      NULL
    REFERENCES sesiones(id) ON DELETE SET NULL,
  ADD COLUMN motivo_revocacion  VARCHAR(50)  NULL;

ALTER TABLE sesiones
  RENAME COLUMN token TO token_legacy;

CREATE INDEX idx_sesiones_familia ON sesiones(familia_id);

-- ---------- 2. reglas que el backend debe cumplir ----------
-- - Login/password+TOTP OK -> crea sesión con token_hash + familia_id
--   nueva (gen_random_uuid()), JWT access de 15 min en memoria.
-- - Refresh: exige token_hash vigente y no revocado; emite fila nueva
--   en la misma familia, marca la vieja (revocado_en + reemplazado).
-- - Reuso de refresh ya rotado -> revocar familia entera
--   (posible robo) + forzar re-login.
-- - Logout: revocado_en por sesión; "cerrar todo" revoca por familia.
-- - Job de limpieza: borrar sesiones expiradas/revocadas viejas.
-- - Endpoint admin: resetear 2FA de un usuario bloqueado (anula
--   totp_habilitado + secreto, exige re-enrolar con QR).
--
-- CUTOVER: cuando el backend solo use token_hash, en una migración
-- posterior: backfill de verificación, token_hash SET NOT NULL,
-- DROP COLUMN token_legacy.
