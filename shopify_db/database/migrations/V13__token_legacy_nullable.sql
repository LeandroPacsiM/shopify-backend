-- ==========================================================
-- MIGRACIÓN V13: token_legacy nullable (transición V8)
-- Las sesiones nuevas solo usan token_hash; la columna legacy queda
-- NULL hasta el cutover, que la elimina. Hallado por AuthFlowTest.
-- No editar después de aplicarlo.
-- ==========================================================

ALTER TABLE sesiones
  ALTER COLUMN token_legacy DROP NOT NULL;
