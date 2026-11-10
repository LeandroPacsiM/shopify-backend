-- ==========================================================
-- INFORME F1 (plan v5, D5): propietarios pendientes de confirmar.
-- Solo lectura. Por tienda: candidato = admin más antiguo por
-- creado_en (NO se asigna automáticamente); n° admins/operadores;
-- owners actuales (siempre 0 antes de V22). La confirmación la hace
-- SUPER_ADMIN en F3 (POST /api/ops/tiendas/:id/propietario).
-- ==========================================================

SELECT t.id AS tienda_id, t.slug, t.nombre,
  (SELECT u.email FROM tienda_usuarios tu
    JOIN usuarios u ON u.id = tu.usuario_id
   WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'admin'
   ORDER BY tu.creado_en ASC LIMIT 1) AS candidato_email,
  (SELECT tu.creado_en FROM tienda_usuarios tu
   WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'admin'
   ORDER BY tu.creado_en ASC LIMIT 1) AS candidato_desde,
  (SELECT COUNT(*) FROM tienda_usuarios tu
   WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'admin') AS n_admins,
  (SELECT COUNT(*) FROM tienda_usuarios tu
   WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'operador') AS n_operadores,
  (SELECT COUNT(*) FROM tienda_usuarios tu
   WHERE tu.tienda_id = t.id AND tu.rol_tienda = 'store_owner') AS n_owners
FROM tiendas t
ORDER BY t.id;
