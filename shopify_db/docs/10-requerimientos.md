# 10 — Requerimientos funcionales y no funcionales

> Alcance: plataforma SaaS Multi-Tienda (tenants) y Multi-Sucursal (locations).
> Lista completa de lo decidido hasta hoy (V1 + V2–V12, decisiones A, mock-first, multi-tenant, SPA único). Estado por ítem: **BD** = impuesto por la base, **BE** = pendiente del backend, **FE** = pendiente del frontend.

## RF-AUTH — Identidad y 2FA

| ID | Requerimiento | Estado |
|---|---|---|
| RF-AUTH-01 | Registro con email + password (hash Argon2id/BCrypt), crea usuario + cliente + rol inicial en una transacción | BE |
| RF-AUTH-02 | Enrolamiento TOTP obligatorio: QR → primer código → habilitado + 10 backup codes; sin enrolar no hay sesión | BD + BE |
| RF-AUTH-03 | Login: password, luego challenge TOTP o backup code; JWT 15 min + refresh opaco rotado por familia (V8) | BD + BE |
| RF-AUTH-04 | Lockout: contar intentos, bloquear tras el umbral; respuesta genérica que no revela si el email existe | BD + BE |
| RF-AUTH-05 | Reuso de refresh rotado revoca la familia entera; logout revoca por sesión/familia; job limpia expiradas | BE |
| RF-AUTH-06 | Reset de 2FA por admin para usuarios bloqueados (re-enrolar con QR) | BE |
| RF-AUTH-07 | Verificación de email y recuperación por código vía `tokens_cuenta` (V15: un solo uso, 24h/60min, solo hash); sin email verificado no hay sesión (el enrolamiento sí puede antes) | BD + BE |
| RF-AUTH-08 | RBAC Global: `super_admin` (dueño del software/plataforma). RBAC por tienda: `admin` (dueño de tienda) / `operador` (empleado) / `cliente`. Denegación por defecto + titularidad por recurso | BD + BE |

## RF-STORE — Tienda (solo cliente, `/tienda`)

| ID | Requerimiento | Estado |
|---|---|---|
| RF-STORE-01 | Home, PLP por categoría/colección, PDP con variantes, búsqueda y etiquetas; solo variantes activas de productos activos | BE + FE |
| RF-STORE-02 | Cuenta del cliente: perfil, direcciones, pedidos, tracking, wishlist, reseñas propias (todo con 2FA) | BE + FE |

## RF-CART — Carrito y checkout

| ID | Requerimiento | Estado |
|---|---|---|
| RF-CART-01 | Carrito anónimo por token; al autenticarse se asocia al cliente; una línea por variante | BD + BE |
| RF-CART-02 | Checkout exige cliente + 2FA (sin guest, decisión A); congela totales con redondeo uniforme; guarda contacto/notas y las copia al pedido (V17) | BD + BE |
| RF-CART-03 | Confirmación transaccional: pedido + líneas + direcciones + impuestos snapshot; checkout completado, carrito convertido, stock reservado | BE |

## RF-ORDER — Pedidos

| ID | Requerimiento | Estado |
|---|---|---|
| RF-ORDER-01 | Máquina de estados con historial (`pendiente→pagado→en_preparacion→enviado→entregado`, `cancelado`, `reembolsado`) | BD + BE |
| RF-ORDER-02 | Cancelar con reembolso y reposición; archivar lógico; prohibido DELETE con historial (V4) | BD + BE |
| RF-ORDER-03 | Todo pedido tiene cliente (V5); GDPR = anonimizar PII (`DELETE /api/storefront/:slug/cuenta`: borra identidad y contacto, conserva pedidos/auditoría), jamás borrar | BD + BE |

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
| RF-REV-01 | Una reseña por cliente y producto (V6), calificación 1–5, publicación moderada; se conserva si el producto se borra (V4) | BD + BE |
| RF-REV-02 | Wishlist sin duplicados por lista | BD + BE |

## RF-TENANT — Aislamiento multi-tienda

| ID | Requerimiento | Estado |
|---|---|---|
| RF-TENANT-01 | Filtro Tenant: todo recurso (producto, pedido, inventario, cliente, descuento) pertenece a una `tienda_id` y está estrictamente aislado; ninguna query cruza tiendas | BD + BE |
| RF-TENANT-02 | El `tienda_id` se deriva de la identidad/autorización, nunca del request; tests anti-fuga entre tiendas | BE |
| RF-TENANT-03 | `super_admin` opera a nivel plataforma (lista y agregados de tiendas, compliance); prohibido leer o modificar datos internos de tiendas; los roles de tienda solo ven la suya | BE + FE |

### Contrato backend (sin código aún: criterios de aceptación)

- Cada request autenticado resuelve `(usuario, tienda_id)` una sola vez en el filtro de seguridad: el slug/path lo propone el cliente, la pertenencia la confirma `tienda_usuarios`/`clientes`/`usuario_roles`. Si no hay vínculo, 403 (nunca 404 con fuga de existencia).
- Toda query de lectura/escritura incluye `tienda_id = :tienda` (o join que lo garantice para tablas derivadas: envíos/pagos→pedido, reseñas→producto/cliente, niveles→ubicación).
- Coherencia cruzada en la misma transacción: pedido, cliente, checkout, dirección, descuento, método de envío y credencial de pasarela deben ser de la misma tienda; si no, 422 sin cambios parciales.
- `super_admin` opera solo vía `/api/ops` y limitado a agregados, compliance y plataforma (nunca datos internos de tiendas); cada acción queda en `registros_auditoria` con tienda afectada.
- Tests obligatorios: leer/escribir con JWT de tienda A sobre recurso de tienda B → 403 y 0 filas; seed de 2 tiendas (tarea 4) como fixture.

## RF-ADMIN — Plataforma (`/` + `/admin`, solo propietario)

| ID | Requerimiento | Estado |
|---|---|---|
| RF-ADMIN-01 | CRUD catálogo (productos, opciones, variantes sin combinaciones ambiguas), colecciones, etiquetas | BE + FE |
| RF-ADMIN-02 | Inventario por ubicación: entradas/salidas/reservas atómicas con bloqueo; desactivar antes de eliminar ubicación | BD + BE |
| RF-ADMIN-03 | Operar pedidos, pagos, reembolsos, envíos y cupones; ver auditoría (`registros_auditoria`) | BE + FE |
| RF-ADMIN-04 | Gestionar usuarios/roles; JWT admin nunca compra en checkout | BE + FE |
| RF-ADMIN-05 | Bandeja de notificaciones por tienda (`notificaciones` V16 + campana WS): listar, badge no-leídas, marcar leída; aislada por tienda | BD + BE |

## RF-BILL — Facturación de plataforma

| ID | Requerimiento | Estado |
|---|---|---|
| RF-BILL-01 | Suscripción al software POR tienda (`suscripciones` V18: una por `tienda_id`, plan mensual/anual, estados pendiente/activa/vencida/cancelada); el dinero del comprador nunca pasa por la plataforma | BD + BE |
| RF-BILL-02 | Webhook de plataforma con firma + dedup por event-id: pagada → activa + periodo + descongela; vencida → congela (`activa=FALSE`) | BE |
| RF-BILL-03 | Tienda congelada (inactiva o sin suscripción vigente): sin checkout nuevo ni lecturas, salvo su facturación para pagar y reactivar; cobro Stripe real V19 (customer + subscription con price por plan, `invoice.payment_succeeded` renueva, `subscription.deleted` cancela) | BE |

## RNF — No funcionales

| ID | Requerimiento | Verificación |
|---|---|---|
| RNF-SEG-01 | Sin secretos en repo/logs/BD (hash + cifrado, `service_role` solo backend) | Revisión + tests |
| RNF-SEG-02 | Checkout/Admin `no-store`, `noindex`; CORS restringido; TLS siempre | Headers + config |
| RNF-SEG-03 | No guardar tarjetas/CVV; dinero solo vía pasarela; PCI fuera de alcance | Revisión |
| RNF-SEG-04 | Correo (2FA/recuperación) por API HTTPS, nunca SMTP directo (bloqueado en Render free). Proveedor conmutable: Resend (default) o Brevo (`mail.proveedor`, llave por entorno); sin llave, modo log | Config prod |
| RNF-SEG-05 | Panel del dueño del software en ruta ofuscada (`/ops-core-management`), protegida con falso 404 y restricción de IP, solo `super_admin`. **Excepción deliberada: Shopify no hace esto** (URLs públicas + auth fuerte); aquí simulamos ser los dueños de la plataforma | BE + FE |
| RNF-REND-01 | Listados paginados; consultas cubiertas por índices (53→55 explícitos); medir antes de optimizar | `EXPLAIN` + stats |
| RNF-DISP-01 | Dev local + pgAdmin; prod Supabase (backups) + Render; `DATABASE_URL` por entorno | Deploy |
| RNF-MANT-01 | Cambios por Flyway inmutable + docs sincronizados en la misma revisión + ADR | Proceso |
| RNF-MANT-02 | Suite de concurrencia: doble checkout, sobreventa, webhook duplicado, último uso de cupón | Tests BE |
| RNF-LEG-01 | GDPR: anonimizar, no borrar; retener auditoría según política | BE |
| RNF-UX-01 | Español, responsive, rutas por superficie y tienda (`/t/:slug`, `/checkout`, `/admin`, `/ops-core-management`) con guards | FE |
