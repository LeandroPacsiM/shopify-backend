# ADR-016 — Desacople total de autenticación (Alternativa B)

Fecha: 2026-11-10. Estado: aplicado en F4 (V20–V23). No borra ADRs anteriores.

## Contexto

El diagnóstico aprobado mostró que `usuarios` mezcla dos mundos: el registro
(`POST /auth/registro`) exige `tiendaSlug` y crea comprador de una tienda
existente (`AuthService.registrar`), `clientes.usuario_id` es UNIQUE global
(un email solo compra en una tienda) y todo usuario registrado arrastra un
perfil comprador. Referencia funcional: Shopify separa Shopify ID (propietarios
y empleados, admin) de Customer Accounts (compradores, passwordless por email).

## Decisión

Desacople total (Alternativa B) con 5 roles canónicos:

- Ámbito administrativo (`usuarios` + `tienda_usuarios` + `usuario_roles` solo
  `super_admin`): STORE_OWNER (1 por tienda), ADMIN, OPERATOR, SUPER_ADMIN.
- Ámbito compras (`clientes` por tienda + `cliente_sesiones` + `cliente_codigos`):
  CUSTOMER con passwordless (código 6 dígitos, TTL 10 min, 5 intentos, hash
  HMAC, rate limit por tienda/email e IP, reenvío 60 s). El perfil nace al
  verificar, no al solicitar. Access 15 min + refresh 7 días con rotación.
- Mismo email permitido en ambos ámbitos, incluso en la misma tienda, sin
  compartir credenciales, sesiones ni permisos. JWT con audiencias disjuntas
  (`tienda-admin` vs `tienda-storefront`) que nunca se intercambian.

Decisiones D1–D7 (registro-administrativo separado sin permisos; sesiones y
códigos CUSTOMER; huérfanos conservados con informe; owner sin automatismo con
confirmación SUPER_ADMIN; tienda nueva = creador owner) y plan por fases
F0–F4 con recuperación definida: ver plan v5 aprobado. La decisión A (2FA para
todos, sin guest) queda derogada para CUSTOMER; sigue vigente para staff.

## Consecuencias

- `clientes.usuario_id` → nullable (V20) → DROP (V23, con respaldo blindado
  `_respaldo_cliente_usuario`, retención 90 días, sin acceso desde la app).
- `tienda_usuarios.rol_tienda` admite `store_owner` (V21, índice ≤1) + trigger
  diferido de obligatoriedad (V22, con advisory lock por tienda).
- `usuario_roles`: solo `super_admin`; se eliminan las filas `cliente`/`admin`/`operador`.
- Historial intacto: todo cuelga de `clientes.id`, que no cambia; cero purgas (D4, con informe de huérfanos en `docs/informes/usuarios-huerfanos.sql`).
- Cutover completo en F4: `auth.customer.enabled=true` por defecto (kill-switch por entorno).
- Retención del respaldo: 90 días desde V23; borrado con la migración V24 (futura,
  ver `docs/informes/drop_respaldo_futuro.sql`); acceso solo superusuario
  por psql; la app no tiene entidad ni repositorio (test
  `RespaldoAusenteTest`); en prod el dueño de la tabla debe ser distinto
  del usuario de conexión de Spring Boot.
