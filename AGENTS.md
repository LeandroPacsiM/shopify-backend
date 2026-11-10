# AGENTS.md

## Stack decidido (no adivinar)

- Backend: Spring Boot 4.1.2 + Java 17 en `backend/` (`com.tienda.plataforma`, deps web/security/jpa/validation/flyway/postgres/googleauth). No scaffoldear Express por los PDFs de `Clases/`.
- BD dev (ahora, todo local): PostgreSQL local + pgAdmin. Fuente de verdad del esquema: `shopify_db/database/01_esquema_shopify.sql` + `shopify_db/database/migrations/V2–V23.sql` (63 tablas, triggers V22 de owner). Prod (al final): BD en Supabase, despliegue en Render; se apunta vía `DATABASE_URL` / connection string del proyecto.
- Frontend: un solo SPA (React + Vite propuesto) con 3 route trees: `/`+`/admin` (staff), `/tienda` (CUSTOMER), `/checkout` (aislado). Ver `diagramas/01-arquitectura-frontend.md`. No partir en 2 apps sin pedirlo.
- Terminología canónica aplicada en F4 (ADR-016): STORE_OWNER (1 por tienda) / ADMIN / OPERATOR / SUPER_ADMIN (solo Ops) / CUSTOMER (passwordless por código, `aud=tienda-storefront`). 2FA TOTP solo para staff. Tiendas nuevas: el creador nace STORE_OWNER. `POST /auth/registro` eliminado; staff por `/auth/registro-administrativo`.
- 2FA TOTP obligatorio para staff, sin guest checkout (decisión A, vigente para staff). Login staff: password → challenge TOTP → JWT corto + refresh rotado.
- Pagos: mock con interfaz adaptador primero, Stripe test después. No construir 4 adaptadores simétricos (Yape/Culqi no son Stripe).

## Qué leer antes de tocar código

- Intento actual: `diagramas/00-contexto-y-superficies.md`, `diagramas/01-arquitectura-frontend.md`.
- Esquema real: `shopify_db/database/01_esquema_shopify.sql` — manda sobre cualquier doc.
- Reglas que el backend debe implementar: `shopify_db/docs/04-reglas-negocio.md`, `06-seguridad-e-integridad.md`, `08-observaciones.md`.
- `shopify_db/docs/` es línea base 1.0 y describe el objetivo prod (Supabase + Render). Dev actual: todo local. Si un diagrama no distingue dev/prod, manda esta lista.
- `Clases/*.pdf` es material de clase, no requisitos. Solo aplica: CRUD, relacional + Flyway, JWT + RBAC, 2FA TOTP, WebSocket solo para notificaciones Admin. No BaaS directo desde frontend, no NoSQL, no Redis/segunda BD.

## API por superficie (no mezclar)

- `/api/storefront` — catálogo, carrito, cuenta CUSTOMER (objetivo B: JWT `aud=tienda-storefront`, sin rol global).
- `/api/checkout` — orden, cotizar envío, pagos, confirmar (CUSTOMER verificado por código, `no-store`, `noindex`).
- `/api/admin` — CRUD catálogo, inventario, pedidos, cupones, envíos (STORE_OWNER/ADMIN/OPERATOR por `tienda_usuarios`, auditoría).
- JWT CUSTOMER nunca abre `/admin`; JWT staff no compra en `/checkout`. El guard de frontend no es seguridad: revalidar rol + titularidad en backend.

## Duda de negocio Shopify: investigar, no adivinar

- El usuario no conoce Shopify a fondo: ante cualquier pregunta de regla de negocio (guest checkout, impuestos, descuentos, envíos, cuentas...), lanzar un subagente que investigue en la doc oficial (`help.shopify.com`, `shopify.dev`) y decidir en base a esa evidencia.
- Guardar solo la decisión final + fuente en 1–3 líneas; no acumular ensayos en este archivo.

## Trampas de la BD v1 (verificadas en docs)

- `actualizado_en` lo refrescan triggers V7; `registros_auditoria` la escribe el backend en la misma transacción (`06`, `08-observaciones.md:160-166`).
- `CASCADE` destruye historia: borrar `pedidos` borra pagos/eventos/reembolsos/envíos. Desde V4: reseñas `SET NULL` y ubicaciones `RESTRICT`. Prohibir DELETE de pedidos/productos/ubicaciones con historial; cancelar/archivar/desactivar en su lugar.
- `clientes` sin `usuario_id` (V23, respaldo `_respaldo_cliente_usuario` 90 días); `pedidos.cliente_id` NOT NULL + `RESTRICT`: la BD bloquea borrar clientes con historial. La baja es anonimizar (GDPR), nunca DELETE. Huérfanos: se conservan (D4, informe en `docs/informes/usuarios-huerfanos.sql`).
- Referencias sin FK: `metacampos(entidad,entidad_id)`, `registros_auditoria(entidad,entidad_id)`, `tasas_impuesto` huérfana (no enlaza a checkout/pedidos). Validar existencia y tipo en aplicación.
- Faltaban UNIQUEs que las migraciones V3/V6 ya imponen en BD (dirección por tipo, descuento por pedido, reseña por cliente-producto, línea por envío, refs externas por pasarela/pago). El backend conserva: validar pertenencia cruzada, sumas contra totales, transiciones y concurrencia con bloqueo.
- `sesiones.token_legacy` en claro solo durante la transición V8 → cutover a `token_hash` + `familia_id`/`revocado_en`. Nunca loguear tokens, secretos ni `pago_eventos.payload` completo.
- Totales, `usos_actuales`, cronología y máquinas de estado no los valida la BD: recalcular en servidor, redondeo `NUMERIC` uniforme, idempotencia por referencia externa en webhooks.

## Cambios de BD

- Orden de `shopify_db/docs/README.md`: SQL primero → `02-modelo-datos.md` + `03-diccionario` en la misma revisión → `04/05/06` si cambian constraints → ADR nuevo sin borrar el anterior.
- `backend/src/main/resources/db/migration/V1__base.sql` es copia congelada de `01_esquema_shopify.sql` (Flyway exige nombre `V*`); V2+ se leen de `shopify_db/database/migrations` vía `spring.flyway.locations`. No editar la copia.
- Nunca editar migraciones ya aplicadas; nueva versión + estrategia de limpieza de duplicados/huérfanos antes de añadir UNIQUE/FK.
- Seeds fuera de migraciones de producción (v1 no trae seed ejecutable).

## Dev local vs Prod (Supabase + Render)

- Ahora (dev): todo local — PostgreSQL local + pgAdmin para ver tablas/esquema/datos, backend y frontend en local. Misma BD `shopify_db`, mismas migraciones Flyway que luego irán a prod.
- Al final (prod): BD en Supabase, backend + frontend en Render. Visualización de presentación: **Table Editor** y **SQL Editor** de Supabase. Fuente: `supabase.com/docs/guides/database/overview`.
- El backend se conecta por `DATABASE_URL` en variables de entorno (local ahora, connection string Supabase después); nunca en el repo.
- Todo acceso a datos pasa por el backend Spring Boot. Clave `service_role` solo en el backend; no exponer PostgREST/Storage directo al frontend sin RLS probada (`06-seguridad-e-integridad.md:309-310`).
- Límites Free a tener en cuenta: la web free de Render se duerme a los 15 min sin tráfico y **bloquea SMTP (25/465/587)**: el correo de 2FA/recuperación debe salir por API HTTPS (Resend/SendGrid), no por SMTP directo.

## Operativa Windows / repo

- Shell es `pwsh`: usar `Get-ChildItem -Recurse -File`, no `ls -la` ni `head`.
- Preferir herramientas `read`/`edit`/`write` sobre `cat`/`sed`/`echo`.
- Responder en español. Citar código como `ruta:línea`.
- Backend con Maven wrapper: `./mvnw test` desde `backend/`. Surefire fija el perfil `test` y la BD local aislada `shopify_db_test`; nunca ejecutar la suite contra `shopify_db_demo`, que queda para Postman. Si falta, crear `shopify_db_test` una sola vez. Fixtures persistentes de tests pueden quedarse en esa BD. `frontend/` sigue vacío: no inventar comandos allí.
- Boot 4.1 divide starters: MockMvc está en `spring-boot-starter-webmvc-test` (`org.springframework.boot.webmvc.test.autoconfigure`), Flyway auto-config en `spring-boot-starter-flyway` + `flyway-database-postgresql` explícito (sin él, PG 18 dice "Unsupported Database"). Lombok con `annotationProcessorPaths` explícito.
- Demo/exposición: `postman/coleccion.json` + `postman/entorno-local.json`; Swagger UI en `/swagger-ui.html` (springdoc). WS solo campana staff (`/ws`, salas `/topic/tienda/:id`): ver `diagramas/09-websocket.md`.
- Webhooks Stripe en local (dev): `stripe listen --events payment_intent.succeeded --events payment_intent.payment_failed --events charge.refunded --events refund.created --events refund.updated --events refund.failed --forward-to localhost:8080/api/webhooks/pagos/stripe` y otro igual a `/api/webhooks/facturacion/stripe`. Cada `listen` imprime un `whsec_...` nuevo: va a la credencial de la tienda (`sk;whsec`) o a `STRIPE_PLATFORM_WHSEC`. En prod no hay CLI: endpoint público registrado en Dashboard → Developers → Webhooks.

## Billing Shopify verificado (subagente, doc oficial 2026-11-08)
- Plan POR TIENDA (cada tienda se factura aparte; solo Plus comparte contrato) + transaction fee extra con pasarela externa; el dinero del comprador va a la cuenta del merchant, nunca a Shopify (`help.shopify.com`: pricing-overview, manage-store-billing, third-party-transaction-fees, getting-paid). Apps cobran al merchant vía Shopify Billing (recurrente 30d/anual + uso con tope, una suscripción activa por app; `shopify.dev`: app-pricing, revenue-share). Impago = tienda congelada (sin storefront/admin, solo billing) y baja solo tras ~60 días (`billing-extensions`, `frozen-store`).
- Decisión: suscripción de plataforma POR `tienda_id` (30d + anual) con nuestras llaves; cada tienda cobra en SU pasarela; impago = `tiendas.activa=FALSE` (congelar, nunca DELETE con historial).
- Decisión 2026-11-08: todo queda en modo prueba (Stripe test, correo en log). Modo `vivo` bloqueado en código; no hay cobros reales.
- Decisión 2026-11-09 (onboarding, doc oficial Shopify: sign-up, free-trial, invite-users, roles): el comerciante crea su tienda tras registrarse; la tienda nace con suscripción `pendiente` (equivale al trial: sin checkout hasta activar) y el creador queda `admin`; staff/equipo por email como en Shopify (nuestro agregar es directo, sin expiración de 7 días).
- Decisión 2026-11-10 (trial pre-2FA estilo-Shopify, ADR-017): sesión staff limitada pre-2FA (JWT `mfa=pendiente`: crear tienda + enrolar + lectura/catálogo) con 2FA obligatorio al activar cobros; JWT completo exige enrolar + verificar (enmienda RF-AUTH-02). Fuente: `help.shopify.com` (free-trial, secure-sign-in, requiring-secure-sign-in).
- Decisión 2026-11-09 (impuestos, doc oficial Shopify: taxes, manual-tax-settings, taxes/shipping-tax, combining-discounts/taxes): coincide descuento-antes-de-impuesto y precios sin impuesto; choca tasa-única (Shopify: por destino del comprador) y envío sin impuesto (Shopify: configurable). Cambio: tasa por departamento/país de envío con fallback + envío gravable por método (V20); exenciones e impuesto-incluido diferidos.
