-- Migración repetible: rol base del RBAC (referencia, no demo).
-- Idempotente: se puede re-ejecutar sin duplicar. Objetivo B (ADR-016):
-- solo `super_admin` global; `admin`/`operador` viven en tienda_usuarios
-- y CUSTOMER en clientes. V23 borró las filas antiguas.
INSERT INTO roles (nombre, descripcion) VALUES
  ('super_admin', 'Dueño plataforma')
ON CONFLICT (nombre) DO NOTHING;
