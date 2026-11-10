-- ==========================================================
-- INFORME F4b (plan v5, D4): usuarios huérfanos tras el desacople.
-- Solo lectura. NO purgar (decisión aprobada: conservar + evaluar).
-- Huérfano = usuario sin vínculo staff (tienda_usuarios), sin rol
-- global (usuario_roles) y sin perfil CUSTOMER ligado. Tras V23 ningún
-- CUSTOMER apunta a usuarios, así que la columna `tiene_customer`
-- siempre es 0 y queda como testigo de la migración.
-- ==========================================================

SELECT u.id, u.email, u.activo, u.email_verificado, u.creado_en,
  u.ultimo_acceso,
  EXISTS (SELECT 1 FROM tienda_usuarios tu WHERE tu.usuario_id = u.id) AS en_equipo,
  EXISTS (SELECT 1 FROM usuario_roles ur WHERE ur.usuario_id = u.id) AS con_rol_global,
  EXISTS (SELECT 1 FROM sesiones s WHERE s.usuario_id = u.id AND s.revocado_en IS NULL) AS con_sesion_vigente
FROM usuarios u
WHERE NOT EXISTS (SELECT 1 FROM tienda_usuarios tu WHERE tu.usuario_id = u.id)
  AND NOT EXISTS (SELECT 1 FROM usuario_roles ur WHERE ur.usuario_id = u.id)
ORDER BY u.creado_en;
