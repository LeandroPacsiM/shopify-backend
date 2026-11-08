-- ==========================================================
-- MIGRACIÓN V12: roles por tienda (scoping multi-tenant)
-- Tarea 3 del plan. Modelo en dos niveles (estilo Shopify
-- organization/store):
-- - Global (tabla usuario_roles existente): super_admin y futuros
--   roles de plataforma. Valen en todas las tiendas, pero SOLO como
--   autorización para /api/ops (lista/agregados/compliance); nunca
--   como pase para /api/admin|storefront|checkout con tienda_id.
-- - Por tienda (tienda_usuarios nueva): admin (dueño) / operador
--   (empleado). El rol cliente se deriva de clientes (usuario_id +
--   tienda_id) y no se duplica aquí.
-- No editar después de aplicarlo.
-- ==========================================================

CREATE TABLE tienda_usuarios (
  id BIGSERIAL PRIMARY KEY,
  tienda_id BIGINT NOT NULL REFERENCES tiendas(id) ON DELETE CASCADE,
  usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  rol_tienda VARCHAR(20) NOT NULL,
  creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_tienda_usuario UNIQUE (tienda_id, usuario_id),
  CONSTRAINT chk_tienda_rol CHECK (rol_tienda IN ('admin', 'operador'))
);

CREATE INDEX idx_tienda_usuarios_usuario ON tienda_usuarios(usuario_id);

-- NOTAS:
-- - Un usuario, un rol por tienda. Para cambiarlo: UPDATE, no duplicar.
-- - El seed (tarea 4) crea el rol global 'super_admin' en roles +
--   su fila en usuario_roles, y asigna admin/operador por tienda aquí.
-- - El backend resuelve autoridad = roles globales + rol de la tienda
--   del contexto (tarea 7); el JWT lleva la tienda activa.
