-- Migración repetible: roles base del RBAC (referencia, no demo).
-- Idempotente: se puede re-ejecutar sin duplicar. No editar lo aplicado;
-- nuevos roles van en otra repetible o en V nuevu.
INSERT INTO roles (nombre, descripcion) VALUES
  ('super_admin', 'Dueño plataforma'),
  ('admin', 'Dueño tienda'),
  ('operador', 'Empleado'),
  ('cliente', 'Comprador')
ON CONFLICT (nombre) DO NOTHING;
