# 10 — Requerimientos funcionales y no funcionales

> Alcance: plataforma SaaS Multi-Tienda (tenants) y Multi-Sucursal (locations).
> Lista completa de lo decidido hasta hoy (V1 + V2–V19, decisiones A, mock-first, multi-tenant, SPA único). Estado por ítem: **BD** = impuesto por la base, **BE** = pendiente del backend, **FE** = pendiente del frontend.
> Terminología canónica (ADR-016, objetivo B): ámbito administrativo = STORE_OWNER (1 por tienda) / ADMIN / OPERATOR / SUPER_ADMIN (global); ámbito compras = CUSTOMER. Donde un RF aún describe el estado pre-B se marca **(actual)**; el objetivo se marca **(objetivo B)**.

## Glosario canónico (ADR-016)

| Término | Ámbito | Dónde vive |
|---|---|---|
| STORE_OWNER | administrativo: propietario único de una tienda | `tienda_usuarios.rol_tienda='store_owner'` (objetivo B; actual: `admin` sin distinción) |
| ADMIN | administrativo: administrador de tienda | `tienda_usuarios.rol_tienda='admin'` |
| OPERATOR | administrativo: empleado con permisos específicos | `tienda_usuarios.rol_tienda='operador'` |
| SUPER_ADMIN | plataforma: administrador general del software | `usuario_roles` global (solo Ops) |
| CUSTOMER | compras: comprador de un storefront | `clientes` por tienda + `cliente_sesiones` (objetivo B; actual: `clientes.usuario_id` UNIQUE global + rol global `cliente`) |

## RF-AUTH — Identidad y 2FA

| ID | Requerimiento | Estado |
|---|---|---|
| RF-AUTH-01 | (actual) Registro con email + password, crea usuario + cliente + rol inicial en una transacción; exige `tiendaSlug`. Objetivo B: se divide en RF-AUTH-09 (staff) y RF-AUTH-10 (CUSTOMER) | BE |
| RF-AUTH-02 | (actual) Enrolamiento TOTP obligatorio para todo registrado. Objetivo B: solo staff; CUSTOMER usa passwordless (decisión A derogada para CUSTOMER) | BD + BE |
| RF-AUTH-03 | (actual) Login staff: password, luego challenge TOTP o backup code; JWT 15 min + refresh opaco rotado por familia (V8). Objetivo B: CUSTOMER con access 15 min + refresh 7 días con rotación y detección de reuso; audiencias disjuntas `tienda-admin` / `tienda-storefront` que nunca se intercambian | BD + BE |
| RF-AUTH-04 | Lockout: contar intentos, bloquear tras el umbral; respuesta genérica que no revela si el email existe | BD + BE |
| RF-AUTH-05 | Reuso de refresh rotado revoca la familia entera; logout revoca por sesión/familia; job limpia expiradas | BE |
| RF-AUTH-06 | Reset de 2FA por admin para usuarios bloqueados (re-enrolar con QR) | BE |
| RF-AUTH-07 | Verificación de email y recuperación por código vía `tokens_cuenta` (V15: un solo uso, 24h/60min, solo hash); sin email verificado no hay sesión (el enrolamiento sí puede antes) | BD + BE |
| RF-AUTH-08 | RBAC canónico (ADR-016): SUPER_ADMIN global en `usuario_roles` (solo Ops); por tienda en `tienda_usuarios`: STORE_OWNER (exactamente 1, V22) / ADMIN / OPERATOR; CUSTOMER independiente en `clientes`, fuera de `usuario_roles`. Denegación por defecto + titularidad por recurso | BD + BE |
| RF-AUTH-09 | (objetivo B, D1) `POST /auth/registro-administrativo`: crea identidad staff sin otorgar ningún permiso; el rol llega solo por equipo o creación de tienda | BE |
| RF-AUTH-10 | (objetivo B, D3) Auth CUSTOMER passwordless: código 6 dígitos (CSPRNG, hash HMAC), TTL 10 min, 5 intentos sin bloqueo permanente, rate limit por tienda/email e IP, reenvío 60 s; el perfil se crea al verificar. Flag `auth.customer.enabled=false` hasta el cutover | BE |

## RF-STORE — Tienda (solo cliente, `/tienda`)

| ID | Requerimiento | Estado |
|---|---|---|
| RF-STORE-01 | Home, PLP por categoría/colección, PDP con variantes, búsqueda y etiquetas; solo variantes activas de productos activos | BE + FE |
| RF-STORE-02 | Cuenta CUSTOMER: perfil, direcciones, pedidos, tracking, wishlist, reseñas propias. Actual: con 2FA; objetivo B: passwordless (decisión A derogada para CUSTOMER) | BE + FE |

## RF-CART — Carrito y checkout

| ID | Requerimiento | Estado |
|---|---|---|
| RF-CART-01 | Carrito anónimo por token; al autenticarse se asocia al CUSTOMER; una línea por variante | BD + BE |
| RF-CART-02 | (actual) Checkout exige cliente + 2FA (sin guest, decisión A). Objetivo B: exige CUSTOMER verificado por código; guarda contacto/notas y las copia al pedido (V17) | BD + BE |
| RF-CART-03 | Confirmación transaccional: pedido + líneas + direcciones + impuestos snapshot; checkout completado, carrito convertido, stock reservado | BE |

## RF-ORDER — Pedidos

| ID | Requerimiento | Estado |
|---|---|---|
| RF-ORDER-01 | Máquina de estados con historial (`pendiente→pagado→en_preparacion→enviado→entregado`, `cancelado`, `reembolsado`) | BD + BE |
| RF-ORDER-02 | Cancelar con reembolso y reposición; archivar lógico; prohibido DELETE con historial (V4) | BD + BE |
| RF-ORDER-03 | Todo pedido tiene CUSTOMER (V5); GDPR = anonimizar PII (`DELETE /api/storefront/:slug/cuenta`: anonimiza perfil y contacto, conserva pedidos/auditoría), jamás borrar. Objetivo B: el anonimizado ya no toca `usuarios` | BD + BE |

## RF-PAY — Pagos

| ID | Requerimiento | Estado |
|---|---|---|
| RF-PAY-01 | Interfaz `PaymentAdapter` (autorizar/capturar/reembolsar); mock con flag `test` primero, Stripe test después (`StripeAdapter`: PaymentIntents + refunds con la llave de la tienda, firma `t,v1` con `whsec`, solo modo `test`, verificado en vivo). Cada tienda registra sus propias credenciales de pasarela (cifradas, solo su `admin` vía `POST /api/admin/pasarelas`); el backend usa las credenciales dinámicas de la tienda del pedido | BD + BE |
| RF-PAY-02 | Múltiples intentos por pedido; webhooks autenticados e idempotentes por event-id (V3); evento crudo Stripe traducido al flujo interno (`POST /api/webhooks/pagos/stripe`, con cotejo de monto) | BD + BE |
| RF-PAY-03 | Reembolsos parciales/totales contra el pago aprobado, sin exceder el saldo | BD + BE |

## RF-SHIP — Envíos

| ID | Requerimiento | Estado |
|---|---|---|
| RF-SHIP-01 | Un pedido en N envíos; líneas parciales entre envíos, sin duplicar en el mismo (V6) | BD + BE |
| RF-SHIP-02 | Tracking: guardar evento externo y mapear a estado local permitido; fuera de orden por `ocurrido_en` | BE |

## RF-DISC — Descuentos

| ID | Requerimiento | Estado |
|---|---|---|
| RF-DISC-01 | Un código, una vez por pedido (V6); códigos distintos combinan (máx. 5 producto/pedido + 1 envío; shipping+shipping nunca) | BD + BE |
| RF-DISC-02 | Vigencia, alcance y `usos_actuales` con consumo atómico y reversión ante cancelación | BE |

## RF-REV — Reseñas y listas

| ID | Requerimiento | Estado |
|---|---|---|
| RF-REV-01 | Una reseña por CUSTOMER y producto (V6), calificación 1–5, publicación moderada; se conserva si el producto se borra (V4) | BD + BE |
| RF-REV-02 | Wishlist sin duplicados por lista | BD + BE |

## RF-TENANT — Aislamiento multi-tienda

| ID | Requerimiento | Estado |
|---|---|---|
| RF-TENANT-01 | Filtro Tenant: todo recurso (producto, pedido, inventario, CUSTOMER, descuento) pertenece a una `tienda_id` y está estrictamente aislado; ninguna query cruza tiendas | BD + BE |
| RF-TENANT-02 | El `tienda_id` se deriva de la identidad/autorización, nunca del request; tests anti-fuga entre tiendas | BE |
| RF-TENANT-03 | SUPER_ADMIN opera a nivel plataforma (lista y agregados de tiendas, compliance); prohibido leer o modificar datos internos de tiendas; los roles de tienda solo ven la suya | BE + FE |
| RF-TENANT-04 | Onboarding: usuario autenticado crea su tienda (`POST /auth/tiendas`, slug único) y queda STORE_OWNER (actual: `admin`) con suscripción `pendiente` (trial: sin checkout hasta activar); un usuario puede tener varias tiendas | BE |

### Contrato backend (sin código aún: criterios de aceptación)

- Cada request autenticado resuelve `(principal, tienda_id)` una sola vez en el filtro de seguridad: el slug/path lo propone el llamante, la pertenencia la confirma `tienda_usuarios`/`clientes`/`usuario_roles`. Si no hay vínculo, 403 (nunca 404 con fuga de existencia). Objetivo B: JWT staff y CUSTOMER con audiencias disjuntas que nunca se intercambian.
- Toda query de lectura/escritura incluye `tienda_id = :tienda` (o join que lo garantice para tablas derivadas: envíos/pagos→pedido, reseñas→producto/CUSTOMER, niveles→ubicación).
- Coherencia cruzada en la misma transacción: pedido, CUSTOMER, checkout, dirección, descuento, método de envío y credencial de pasarela deben ser de la misma tienda; si no, 422 sin cambios parciales.
- SUPER_ADMIN opera solo vía `/api/ops` y limitado a agregados, compliance y plataforma (nunca datos internos de tiendas); cada acción queda en `registros_auditoria` con tienda afectada. Confirma STORE_OWNER de tiendas existentes (plan v5).
- Tests obligatorios: leer/escribir con JWT de tienda A sobre recurso de tienda B → 403 y 0 filas; seed de 2 tiendas (tarea 4) como fixture.

## RF-ADMIN — Plataforma (`/` + `/admin`, solo staff de la tienda)

| ID | Requerimiento | Estado |
|---|---|---|
| RF-ADMIN-01 | CRUD catálogo (productos, opciones, variantes sin combinaciones ambiguas), colecciones, etiquetas | BE + FE |
| RF-ADMIN-02 | Inventario por ubicación: entradas/salidas/reservas atómicas con bloqueo; desactivar antes de eliminar ubicación | BD + BE |
| RF-ADMIN-03 | Operar pedidos, pagos, reembolsos, envíos y cupones; ver auditoría (`registros_auditoria`) | BE + FE |
| RF-ADMIN-04 | Gestionar equipo (STORE_OWNER/ADMIN/OPERATOR, con resguardo de último OWNER); JWT staff nunca compra en checkout | BE + FE |
| RF-ADMIN-05 | Bandeja de notificaciones por tienda (`notificaciones` V16 + campana WS): listar, badge no-leídas, marcar leída; aislada por tienda | BD + BE |

## RF-BILL — Facturación de plataforma

| ID | Requerimiento | Estado |
|---|---|---|
| RF-BILL-01 | Suscripción al software POR tienda (`suscripciones` V18: una por `tienda_id`, plan mensual/anual, estados pendiente/activa/vencida/cancelada); el dinero del CUSTOMER nunca pasa por la plataforma | BD + BE |
| RF-BILL-02 | Webhook de plataforma con firma + dedup por event-id: pagada → activa + periodo + descongela; vencida → congela (`activa=FALSE`) | BE |
| RF-BILL-03 | Tienda congelada (inactiva o sin suscripción vigente): sin checkout nuevo ni lecturas, salvo su facturación para pagar y reactivar; cobro Stripe solo en modo test (decisión 2026-11-08: modo vivo bloqueado, no existe V19): customer + subscription con price por plan, `invoice.payment_succeeded` renueva, `subscription.deleted` cancela | BE |

## RNF — No funcionales

| ID | Requerimiento | Verificación |
|---|---|---|
| RNF-SEG-01 | Sin secretos en repo/logs/BD (hash + cifrado, `service_role` solo backend) | Revisión + tests |
| RNF-SEG-02 | Checkout/Admin `no-store`, `noindex`; CORS restringido; TLS siempre | Headers + config |
| RNF-SEG-03 | No guardar tarjetas/CVV; dinero solo vía pasarela; PCI fuera de alcance | Revisión |
| RNF-SEG-04 | Correo (2FA/recuperación) por API HTTPS, nunca SMTP directo (bloqueado en Render free). Proveedor conmutable: Resend (default) o Brevo (`mail.proveedor`, llave por entorno); sin llave, modo log | Config prod |
| RNF-SEG-05 | Panel de SUPER_ADMIN en ruta ofuscada (`/ops-core-management`), protegida con falso 404 y restricción de IP, solo SUPER_ADMIN. **Excepción deliberada: Shopify no hace esto** (URLs públicas + auth fuerte); aquí simulamos ser los dueños de la plataforma | BE + FE |
| RNF-REND-01 | Listados paginados; consultas cubiertas por índices (53→55 explícitos); medir antes de optimizar | `EXPLAIN` + stats |
| RNF-DISP-01 | Dev local + pgAdmin; prod Supabase (backups) + Render; `DATABASE_URL` por entorno | Deploy |
| RNF-MANT-01 | Cambios por Flyway inmutable + docs sincronizados en la misma revisión + ADR | Proceso |
| RNF-MANT-02 | Suite de concurrencia: doble checkout, sobreventa, webhook duplicado, último uso de cupón | Tests BE |
| RNF-LEG-01 | GDPR: anonimizar, no borrar; retener auditoría según política | BE |
| RNF-UX-01 | Español, responsive, rutas por superficie y tienda (`/t/:slug`, `/checkout`, `/admin`, `/ops-core-management`) con guards | FE |
