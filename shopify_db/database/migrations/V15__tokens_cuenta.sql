-- ==========================================================
-- MIGRACIÓN V15: tokens de cuenta (verificación email + reset)
-- RF-AUTH-07. Tabla `tokens_cuenta`: un solo uso, con expiración,
-- solo guarda el hash (SHA-256); el token plano viaja por correo
-- (Resend HTTPS, modo log sin llave) y jamás va a logs.
-- Expiración la impone el backend: verificación 24h, reset 60min.
-- No editar después de aplicarlo.
-- ==========================================================

CREATE TABLE tokens_cuenta (
  id BIGSERIAL PRIMARY KEY,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  tipo VARCHAR(30) NOT NULL,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expira_en TIMESTAMPTZ NOT NULL,
  usado_en TIMESTAMPTZ NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tokens_cuenta_tipo CHECK (tipo IN ('verificacion_email', 'reset_password'))
);

-- NOTAS:
-- - Al solicitar uno nuevo del mismo tipo se invalidan los pendientes
--   (usado_en=NOW()) en la misma transacción: solo el último vale.
-- - Al confirmar: usado_en=NOW() (no se borra, queda auditoría).
-- - Respuestas genéricas en solicitar (no revelar si el email existe).

CREATE INDEX idx_tokens_cuenta_usuario
  ON tokens_cuenta(usuario_id);
