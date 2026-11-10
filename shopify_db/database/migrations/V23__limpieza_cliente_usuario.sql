-- ==========================================================
-- MIGRACIÓN V23: limpieza definitiva CUSTOMER (Alternativa B, ADR-016)
-- F4b del plan v5 (ajuste 3: separada de V22; ajuste 4 + condición 3
-- de ejecución para el respaldo). Sin retorno: el NOT NULL antiguo de
-- `usuario_id` es irrestaurable una vez hay perfiles sin vínculo.
--   1. Respaldo de trazabilidad (retención 90 días, borrado en V24
--      futura): copia (id, usuario_id). Acceso restringido: REVOKE
--      total a PUBLIC; la app NO tiene entidad ni repositorio sobre
--      esta tabla (test RespaldoAusenteTest); en prod el dueño de la
--      tabla debe ser distinto del usuario de conexión de Spring
--      (endurecimiento documentado en ADR-016, fuera del alcance
--      de esta migración que corre con el mismo rol que Flyway).
--   2. DROP de `clientes.usuario_id` (columna + FK + índices asociados).
--   3. Borrado de roles globales muertos (`cliente`, `admin`,
--      `operador`): sus filas en `usuario_roles` caen por CASCADE
--      (V1); solo queda `super_admin`. `R__roles_base.sql` deja de
--      sembrarlos para que no regresen.
-- No editar después de aplicarlo.
-- ==========================================================

-- ---------- 1. respaldo ----------
CREATE TABLE _respaldo_cliente_usuario (
  cliente_id BIGINT NOT NULL PRIMARY KEY,
  usuario_id BIGINT,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO _respaldo_cliente_usuario (cliente_id, usuario_id)
SELECT id, usuario_id FROM clientes;

REVOKE ALL ON _respaldo_cliente_usuario FROM PUBLIC;

-- ---------- 2. DROP del vínculo ----------
ALTER TABLE clientes DROP COLUMN usuario_id;

-- ---------- 3. roles globales muertos ----------
DELETE FROM roles WHERE nombre IN ('cliente', 'admin', 'operador');
