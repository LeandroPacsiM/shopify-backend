-- ==========================================================
-- REFERENCIA FUTURA (NO es migración: no vive en `migrations/`).
-- Borrado del respaldo V23 tras la ventana de retención de 90 días
-- (plan v5, condición 3). Al cumplir la ventana, copiar este archivo a
-- `shopify_db/database/migrations/V24__drop_respaldo.sql` y desplegar.
-- Verificar antes: 0 incidentes ligados a `clientes.usuario_id`.
-- ==========================================================

-- DROP TABLE _respaldo_cliente_usuario;
