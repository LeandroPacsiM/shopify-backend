# Auditoría integral del proyecto

**Fecha:** 2026-11-08  
**Alcance:** repositorio completo, con `shopify_db/docs/10-requerimientos.md` como criterio funcional y `AGENTS.md`, esquema, migraciones, ADR y diagramas como decisiones técnicas complementarias.  
**Tipo:** revisión estática más consultas de solo lectura a la BD local. No se modificó código ni BD. No se ejecutaron Maven tests ni requests en Postman.

## Dictamen ejecutivo

**El proyecto aún no está listo para publicar sus superficies ni para una demo end-to-end.** La BD local consultada sí tiene las FKs esperadas; el bloqueo principal no es una migración faltante en esa instancia, sino autorización por rol/tenant, controles de pago y webhooks, y una implementación funcional incompleta. Además, no existe frontend en el repo.

Prioridades inmediatas:

1. Rotar/revocar la credencial con formato de API key encontrada en `opencode.json:8` si es válida. No se reproduce su valor en este informe.
2. Cerrar `/api/admin` por rol y hacer que `super_admin` opere exclusivamente por la superficie de Ops.
3. Hacer que el modo de prueba sea imposible de eludir con llaves Stripe reales y autenticar el webhook de tracking.
4. Corregir lockout/revocación de sesiones, bypass de tienda congelada y condiciones de sobrepago/reembolso.
5. Completar los endpoints storefront y el SPA antes de presentar el recorrido de compra.

## Método y límites

- Se contrastaron requerimientos, decisiones, controladores/servicios/repositorios, migraciones, configuración, tests y colección Postman.
- Para `localhost:5432/shopify_db_demo` se usó una transacción PostgreSQL marcada `READ ONLY`; solo se ejecutaron `SELECT`, seguida de `ROLLBACK`. No se aplicó Flyway ni se cambió información.
- No se ejecutó `./mvnw test`: los tests de integración activan `local` y apuntan a `shopify_db_demo`; por ejemplo, `SobreventaTest.java:57-60,101-115` crea fixtures sin `@Transactional` para que los vean sus hilos. Ejecutarlos puede dejar filas en la BD demo.
- No se ejecutó Postman contra una aplicación activa. Por ello, los hallazgos de token/autenticación de Postman se clasifican según el contenido estático, no como reproducción de runtime.
- No se consultó una BD de Supabase/producción ni se verificó la validez de credenciales externas.

En las citas Java se omite por brevedad el prefijo común `backend/src/main/java/com/tienda/plataforma/`; las citas de tests se refieren a `backend/src/test/java/com/tienda/plataforma/`. Las rutas de documentación, BD y Postman se escriben completas.

## Verificación de los tres hallazgos iniciales

| Hallazgo | Estado | Resultado |
|---|---|---|
| La BD demo tiene 0 FKs aunque las migraciones las definen | **Refutado para la BD local consultada** | `shopify_db_demo` devolvió **84 FKs, 0 sin validar**; 59 tablas base públicas en total, incluyendo `flyway_schema_history` (58 tablas de aplicación). Flyway muestra V1–V18 y `R__roles_base`, todas exitosas. Si el hallazgo corresponde a otra conexión, esa instancia aún debe identificarse y consultarse. |
| GET de productos storefront está en Postman pero no en backend | **Confirmado** | La colección define `GET /api/storefront/{{tiendaSlug}}/productos` y detalle por slug (`postman/coleccion.json:133-153`); `StorefrontController.java:25-60` no tiene esos handlers. El listado disponible está bajo `/api/admin/productos` (`AdminCatalogoController.java:37-40`), que no sustituye la API storefront. |
| El auto-guardado del token falla en Postman | **Parcial / no reproducido** | En `coleccion.json:96-104,111-120` sí hay scripts que guardan `accessToken` como variable de colección. Pero el login de ejemplo usa un TOTP fijo (`coleccion.json:89-95`) que normalmente no coincidirá con el código actual de una cuenta; ante respuesta de error, el script no comprueba status ni presencia del token. Además, el request YAML local de refresh no contiene script (`postman/Plataforma Tiendas (local)/Auth/refresh (usa cookie).request.yaml:1-4`), aunque ese directorio está ignorado/no es la colección entregable (`.gitignore:20-25`). Hace falta reproducir en Postman con TOTP válido y saber qué colección se está ejecutando. |

### Evidencia de la BD local

La consulta a `information_schema.table_constraints` dio 84 FKs; una segunda consulta a `pg_constraint` confirmó que ninguna está `NOT VALID`. La tabla Flyway registra V1–V18 con `success=true` y la repetible `roles base` también exitosa. `backend/src/main/resources/db/migration/V1__base.sql` y `shopify_db/database/01_esquema_shopify.sql` tienen SHA-256 idéntico.

El recuento observado concuerda con V1 + migraciones hasta V18: 58 tablas de aplicación y 84 FKs; las 59 tablas observadas incluyen la tabla de historial de Flyway. **No hay evidencia para reconstruir o “arreglar las FKs” de esta instancia local.** Una BD distinta, esquema distinto o conexión distinta puede dar otro resultado.

Consulta inocua para repetir en pgAdmin/`psql`:

```sql
SELECT current_database(), current_schema();

SELECT COUNT(*) AS claves_foraneas,
       COUNT(*) FILTER (WHERE NOT convalidated) AS no_validadas
FROM pg_constraint
WHERE contype = 'f'
  AND connamespace = 'public'::regnamespace;

SELECT version, description, success
FROM public.flyway_schema_history
ORDER BY installed_rank;
```

## Hallazgos críticos y altos

### SEC-01 — Credencial con formato de API key dentro del repo

**Severidad: crítica | Estado: exposición confirmada; validez desconocida.**

`opencode.json:8` contiene un valor literal con formato de API key Figma y `opencode.json` está versionado. Si la credencial es real, puede permitir acceso a recursos asociados a esa cuenta/workspace. **Revocarla/rotarla y retirar la credencial del repo y, si corresponde, de su historial.** No basta con cambiarla localmente. El valor se omite deliberadamente de este documento.

`application-local.properties:11-13` también contiene llaves de desarrollo conocidas por el repo (cifrador/JWT) y `:5` un fallback de contraseña local. Están identificadas como locales, pero no deben reutilizarse fuera de desarrollo; producción debe usar secretos distintos.

### SEC-02 — RBAC incompleto: un cliente puede entrar a rutas administrativas

**Severidad: crítica | Estado: confirmado estáticamente.**

`SecurityConfig.java:80-84` solo exige autenticación para el resto de rutas; no aplica `hasRole` por superficie. `TenantFilter.java:63-70` acepta como vínculo suficiente ser `super_admin`, miembro de `tienda_usuarios` **o cliente** de la tienda. Los controladores de pedidos, catálogo, cupones, notificaciones y facturación no verifican el rol del actor; por ejemplo, `AdminPedidoController.java:32-35` llama al listado con el tenant del filtro, y `AdminPedidoService.java:48-52` devuelve los pedidos de toda esa tienda.

Consecuencias:

- Un JWT de cliente vinculado puede leer/operar endpoints `/api/admin/**` de su tienda, según cada controlador.
- `super_admin` es aceptado por `TenantFilter` como miembro universal y puede llegar a rutas internas de `/api/admin`, aunque V12 prohíbe usar ese rol como pase a dichas superficies (`V12__roles_por_tienda.sql:5-11`; `10-requerimientos.md:79-85`).
- `AdminUsuarioController.java:35-43` no valida que el actor sea admin. `AuthService.resetTotpPorAdmin` comprueba que **el usuario objetivo** pertenezca a la tienda (`AuthService.java:397-405`), no que el actor tenga rol administrativo.

Es un bloqueo antes de publicar: proteger rutas por `admin`/`operador` en servidor, separar explícitamente `super_admin` hacia Ops y agregar pruebas negativas con JWT de cliente y de superadmin para cada controlador administrativo.

### SEC-03 — “Solo test” no está garantizado por el secreto usado

**Severidad: crítica antes de exponer pagos | Estado: confirmado en código; no se verificaron llaves configuradas.**

`TiendaPasarelaService.java:63-83` acepta `modo=test` junto con cualquier valor en `req.secreto()`. `StripeAdapter.exigirTest` comprueba únicamente `modo` (`StripeAdapter.java:86-90`) y `skDe` envía el secreto descifrado a Stripe sin comprobar que empiece por `sk_test_`/`rk_test_` (`:92-95`). Por tanto, se puede registrar una llave `sk_live_...` bajo `modo=test` y el adaptador llamaría a la API real.

El riesgo también existe para facturación de plataforma: `FacturacionService.cobroConfigurado()` solo comprueba que llave y price IDs no estén vacíos, y `crearEnStripe` los usa para crear customer/subscription (`FacturacionService.java:236-263`); no hay validación de prefijo `sk_test_`. Esto contradice la decisión vigente de modo de prueba/live bloqueado (`AGENTS.md:69`, `ADR-013-stripe-test.md:49-51`), aunque la configuración local consultada no demuestra que se haya usado una llave live.

No habilitar cobros hasta que el código rechace llaves live independientemente del campo `modo`, con pruebas para ambos adaptadores y configuración de plataforma. La prueba actual solo rechaza `modo=vivo` (`StripeAdapterTest.java:96-103`), no `sk_live` con `modo=test`.

### SEC-04 — Webhook de tracking público y sin autenticación

**Severidad: alta | Estado: confirmado estáticamente.**

`SecurityConfig.java:81` permite `/api/webhooks/**` sin JWT. Pago Stripe y facturación verifican firma, pero `EnvioController.tracking` (`EnvioController.java:37-40`) no recibe firma/token, y `TrackingWriteRequest.java:7-14` solo contiene tracking, estado y datos del evento. `EnvioService.tracking` inserta el evento y actualiza el envío a estados locales, incluyendo `entregado` (`EnvioService.java:121-149`). Un tercero que conozca/adicione un número de tracking puede falsificar estados y eventualmente marcar el pedido entregado.

Exigir firma/autenticación del transportista, timestamp/ventana de tolerancia e idempotencia antes de permitir cambios de estado.

### AUTH-01 — Lockout y revocación por reuso pueden revertirse

**Severidad: alta | Estado: confirmado por flujo transaccional.**

`AuthService.login` y `verificarTotpLogin` guardan el intento fallido y después lanzan `NoAutorizadoException` (`AuthService.java:199-205,215-238`). La excepción extiende `RuntimeException` (`NoAutorizadoException.java:6-11`). El comportamiento por defecto de `@Transactional` en Spring revierte la transacción ante `RuntimeException` ([documentación oficial](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)); por tanto, los intentos y el bloqueo pueden no persistir.

Lo mismo afecta al reuso del refresh: `refresh` llama `revocarFamilia` y lanza la misma excepción dentro de la transacción (`AuthService.java:247-253,435-443`), lo que puede revertir la revocación de la familia. `AuthFlowTest.java:93-96` solo comprueba que el token viejo falle; no comprueba que el refresh sucesor quede revocado. Separar el registro del fallo/revocación de la excepción que responde 401, o configurar explícitamente el resultado transaccional, y probar el estado persistido después del error.

### TEN-01 — Se puede omitir el tenant y no se compara siempre el slug de URL

**Severidad: alta | Estado: confirmado estáticamente.**

`TenantFilter.java:53-57` continúa la cadena si falta `X-Tienda-Slug`, sin resolver tenant ni aplicar la regla de tienda congelada. Hay rutas que no requieren el header: confirmar checkout (`CheckoutController.java:40-43`) y rutas de cuenta que resuelven tenant desde `{slug}` (`CuentaController.java:113-119,180-191`). `PedidoService.confirmar` comprueba dueño/estado del checkout pero no llama `FacturacionService.exigirActiva` (`PedidoService.java:99-117`); ese control existe al **crear** el checkout (`CheckoutService.java:80-84`), no al confirmarlo. Un cliente puede omitir el header y leer su cuenta o confirmar un checkout pendiente de una tienda congelada.

Además, `TenantFilter` valida el slug del header, pero `StorefrontController` pasa el slug de la ruta a servicios (`StorefrontController.java:48-60`). `CarritoService.obtenerOCrear` busca por token global (`CarritoService.java:50-56`) y no verifica que el carrito encontrado pertenezca a la tienda de la ruta antes de devolverlo (`:65-74`). Falta una comparación obligatoria entre path, header y tenant resuelto, y validar tenant/titularidad en toda recuperación por token.

`TiendaCongeladaTest.java:88-114` solo prueba peticiones con `X-Tienda-Slug`; no cubre ausencia del header, confirmación de checkout ni lectura de cuenta.

### AUTH-02 — Reset de TOTP no verifica rol del actor ni invalida sesiones

**Severidad: alta | Estado: confirmado estáticamente.**

El reset está bajo `/api/admin/usuarios`, pero el controlador solo calcula si el actor es superadmin y llama al servicio; el servicio, cuando no es superadmin, verifica vínculo del **objetivo** a la tienda, no el rol del actor (`AdminUsuarioController.java:35-43`, `AuthService.java:393-417`). Con el bypass de RBAC, un cliente puede resetear TOTP de otra persona de su tienda.

El reset limpia secreto/backup codes pero no revoca sesiones existentes (`AuthService.java:408-417`). `refresh` comprueba vigencia de sesión, pero no exige que TOTP siga habilitado (`AuthService.java:247-265`). Un refresh previamente emitido puede seguir renovando acceso después del reset, en contra del requisito de re-enrolamiento.

### PAY-01 — Carreras permiten sobrecobro y estados de reembolso prematuros

**Severidad: alta | Estado: confirmado por lectura de código; requiere prueba de concurrencia.**

- `crearIntento` calcula el saldo antes de crear el intento (`PagoService.java:72-108`); `restante` resta solo pagos ya `aprobado` (`:326-332`). No cuenta intentos `procesando` ni bloquea pedido. Dos solicitudes concurrentes pueden crear PaymentIntents por el total completo y ambos podrían aprobarse.
- `reembolsar` lee la suma de reembolsos sin bloqueo (`PagoService.java:259-279`) y luego llama al proveedor antes de persistir la fila (`:280-293`). Solicitudes concurrentes pueden exceder el saldo; si el proveedor ejecuta el reembolso pero falla el guardado local, no hay un registro/idempotency key que permita conciliar con seguridad. `StripeAdapter.reembolsar` manda el POST sin idempotency key (`StripeAdapter.java:67-73`).
- En un reembolso total, el servicio marca pago y pedido `reembolsado` al recibir la respuesta inicial, incluso cuando Stripe devuelve `PROCESANDO` (`PagoService.java:284-304`). Si el webhook posterior lo rechaza, solo actualiza la fila de reembolso (`:208-215`), no restaura pedido/pago.

Hace falta un estado de operación durable previo al llamado externo, bloqueo/idempotencia y actualizar pedido/pago solo al confirmar reembolso total.

### ORDER-01 — Cancelación pagada no reembolsa; envío no consume la reserva

**Severidad: alta | Estado: confirmado estáticamente.**

`AdminPedidoService.cancelar` acepta pedidos `pendiente` o `pagado`, libera reserva y cambia a `cancelado`, sin llamar al servicio de pagos ni emitir reembolso (`AdminPedidoService.java:55-74`). Eso deja cobros aprobados sin devolución al cancelar un pedido pagado.

En la confirmación, `PedidoService.reservarStock` incrementa `reservado` (`PedidoService.java:221-253`) y `NivelInventario.vendible()` calcula `disponible - reservado` (`NivelInventario.java:52-55`). `EnvioService` crea envíos y actualiza estados, pero no descuenta/libera inventario al despachar o entregar (`EnvioService.java:66-116,119-159`). La reserva queda ocupada para siempre después de completar una venta. Además, `EnvioService.crear` solo rechaza estados cancelado/entregado/reembolsado y puede crear envío de un pedido pendiente (`EnvioService.java:66-75`), aunque el flujo de `05-flujos-principales.md:349-355` parte de pedido pagado. El flujo de tracking actualiza el estado según el evento recibido sin comparar con `ocurrido_en`; un evento antiguo puede hacer retroceder el envío (`EnvioService.java:121-149`), en contra de `RF-SHIP-02` y `05-flujos-principales.md:392-398`.

### BILL-01 — Facturación externa está a medio completar y sus rutas carecen de RBAC

**Severidad: alta antes de habilitar facturación | Estado: confirmado en código; entorno externo no probado.**

`FacturacionService.suscribir` crea Stripe si hay configuración (`FacturacionService.java:87-108`), pero la respuesta solo devuelve plan/estado/periodo (`SuscripcionResponse.java:5`), no un checkout/URL/secret que permita completar la suscripción `default_incomplete` (`FacturacionService.java:255-263`). `cancelar` solo cambia estado local y no cancela la suscripción en Stripe (`:111-117`). Los llamados externos ocurren dentro de una transacción local y no llevan clave de idempotencia, así que los reintentos pueden dejar customers/subscriptions externos huérfanos.

Además de SEC-02, `AdminFacturacionController.java:31-47` no exige rol `admin`. La documentación se contradice: `V18__suscripciones.sql:7-9` y `ADR-014-suscripciones.md:26-32` dicen que Stripe real llega en V19, mientras el código de cobro está presente; `AGENTS.md:69` declara que modo live está bloqueado. No hay migración V19 en el repo. Aclarar el alcance y mantenerlo desactivado hasta cerrar autorización, flujo de cobro, cancelación e idempotencia.

## Hallazgos funcionales y de entrega

### FE-01 — El frontend requerido no está en el repositorio

**Severidad: alta para demo/entrega | Estado: confirmado por inventario.**

`frontend/` no contiene archivos fuente ni `package.json`; tampoco se encontró un manifiesto de Render, `Dockerfile` o workflow que despliegue frontend/backend. Sin el SPA React/Vite de un solo proyecto decidido en `AGENTS.md:7` y `diagramas/01-arquitectura-frontend.md:3-5`, no están implementados los tres route trees, guards, layouts, pantallas ni flujo visual de compra. `RF-UX-01` queda sin cumplir (`10-requerimientos.md:119`).

### FE-02 — Faltan handlers storefront de catálogo

**Severidad: alta | Estado: confirmado.**

`StorefrontController.java:25-60` solo expone tracking y carrito. No hay `GET productos`, detalle por slug, búsqueda, categorías/colecciones ni búsqueda/etiquetas. El `GET` de Postman no está respaldado por backend. Por tanto `RF-STORE-01` no está implementado aunque aparezca en la colección (`10-requerimientos.md:19-24`). `TiendaCongeladaTest.java:93-96` pide esa ruta y recibe 403 desde `TenantFilter` antes de resolver el handler; no prueba que el endpoint exista. No seguir con el endpoint público hasta cerrar RBAC/tenant y documentar contrato; luego implementar y probarlo.

### FEAT-01 — Backend cubre fragmentos, no los CRUD/flows completos del contrato

**Severidad: alta/media | Estado: parcial.**

- `RF-ADMIN-01/02`: `AdminCatalogoController.java:37-70` ofrece operaciones básicas de producto/variante, pero no CRUD de opciones, colecciones, etiquetas e imágenes; `ProductoService.java:22` declara “sin combinaciones ambiguas por ahora”. No hay controlador administrativo de inventario: faltan ajustes/entradas/salidas por ubicación.
- `RF-ADMIN-04`: no se encontró controlador para gestionar usuarios/roles/invitaciones de tienda; el alta pública solo asigna rol `cliente` (`AuthService.java:137-144`).
- `RF-STORE-02/RF-REV-01/02`: `CuentaController.java:75-170` permite leer/crear/borrar direcciones, listar pedidos, **agregar** wishlist y crear reseña no aprobada. No hay lectura/borrado de wishlist, perfil, endpoint público de reseñas moderadas ni controlador para moderarlas. Las reseñas se guardan `aprobada=false` (`:162-170`) y no hay flujo para publicarlas.
- `RF-DISC-01`: el request de checkout tiene un único `String codigoDescuento` (`CrearCheckoutRequest.java:6-14`), mientras el requisito permite códigos combinados (`10-requerimientos.md:57-62`). La cancelación tampoco revierte `usos_actuales` del cupón aplicado (`AdminPedidoService.java:55-74`).
- `RF-PAY-01`: `PaymentAdapter` define `autorizar`, `reembolsar` y firma, pero no `capturar` (`PaymentAdapter.java:8-23`). El PaymentIntent devuelve solo ID/estado/monto (`PagoResponse.java:5`) y el adaptador no expone `client_secret` (`StripeAdapter.java:55-65`); unido a la ausencia de frontend, el flujo de pago Stripe no está listo para comprador.
- `RF-ORDER-01`: el flujo no evidencia todas las transiciones/historiales. `EnvioService` mueve pedido de pagado a preparación al crear envío y a entregado cuando todos se entregan, pero no transiciona el pedido a `enviado` (`EnvioService.java:113-116,150-159`).
- `RF-STORE-01/02`, `RF-ADMIN-01/03/04`, `RF-SHIP-02` y `RF-BILL-03` no tienen cobertura de API completa en Postman; la existencia de una petición no equivale a que el handler exista ni a que su rol esté probado.

### GDPR-01 — PII de checkout sobrevive a la anonimización

**Severidad: media/alta | Estado: confirmado estáticamente.**

V17 añade email, teléfono y notas a `checkout_sesiones` y el servicio las escribe (`CheckoutService.java:153-155`). `PrivacidadService.anonimizarCuenta` limpia contacto de pedidos, direcciones y reseñas (`PrivacidadService.java:104-125`), pero no toca sesiones de checkout. Esos datos pueden quedar en checkouts completados/abandonados tras la baja, en contra de `RNF-LEG-01`/`RF-ORDER-03` (`10-requerimientos.md:39-40,118`). Agregar una política de retención/anonimización y prueba que cubra checkouts, no solo pedidos.

### AUTH-03 — CORS no incluye las rutas de autenticación

**Severidad: media/alta para el SPA | Estado: confirmado estáticamente.**

`SecurityConfig` solo registra CORS para `/api/**` (`SecurityConfig.java:52-63`), pero login, refresh y logout están en `/auth/**` (`AuthController.java:38-41,68-100`). El SPA local se configura como `http://localhost:5173` (`application-local.properties:15-16`), así que el navegador no tiene CORS configurado para iniciar/renovar sesión. `SeguridadWebTest.java:50-62` prueba únicamente `/api/admin`, no `/auth`. Además, logout revoca el refresh en servidor pero no envía cookie expirada (`AuthController.java:94-100`); el cookie es HttpOnly y usa `Secure` por perfil/SameSite=Lax (`:130-140`).

### AUTH-04 — Los correos en modo log contienen los códigos de un solo uso

**Severidad: media, alta si se habilita fuera de dev | Estado: confirmado; excepción dev documentada.**

Sin API key, Resend/Brevo escriben `para`, asunto y HTML completo al log (`ResendMailService.java:38-42`, `BrevoMailService.java:44-48`). El HTML lleva el token de verificación/reset (`AuthService.java:381-385`). ADR-010 reconoce el modo log local (`ADR-010-tokens-cuenta.md:47-48`), pero V15 dice que el token plano jamás va a logs (`V15__tokens_cuenta.sql:3-6`). La configuración de producción permite que falte la llave y conservar el fallback. Hacer que ese fallback sea imposible en prod o dejar claro que el servicio permanece estrictamente en test; no registrar contenido del correo.

### OPS-01 — Ruta e IP de Ops no implementan todo el requisito

**Severidad: media | Estado: parcial y con conflicto documental.**

El backend expone `/api/ops` (`OpsController.java:30-33`) y sus métodos sí verifican `ROLE_super_admin` (`:99-105`). `RF-TENANT-03` contempla `/api/ops`, pero `RNF-SEG-05` y el diagrama frontend exigen `/ops-core-management`, falso 404 y restricción por IP (`10-requerimientos.md:113`; `diagramas/01-arquitectura-frontend.md:28-34,108-114`). No hay filtro IP/falso 404 en el controller/config inspeccionados ni frontend Ops. Resolver la contradicción del contrato y probar el guardado elegido.

### RNF-01 — Listados sin paginación y auditoría funcional incompleta

**Severidad: media | Estado: confirmado estáticamente.**

`RNF-REND-01` exige listados paginados (`10-requerimientos.md:114`), pero `AdminPedidoService.listar` trae todos los pedidos y filtra estado en memoria (`AdminPedidoService.java:47-52`); cuenta, notificaciones y Ops también devuelven `List`/`findAll`. No se encontraron `Pageable`/`Page` en estos handlers.

La tabla `registros_auditoria` existe, pero su única escritura encontrada es Ops (`OpsController.java:115-123`); CRUD de catálogo, operaciones de pedido, cupón, pasarela, reset TOTP y reembolso no escriben una fila de auditoría. Además, Ops obtiene el actor pero no asigna `fila.usuario` (`:115-122`). Esto incumple la auditoría de acciones de tienda (`AGENTS.md:33`, `10-requerimientos.md:93`).

## Migraciones, documentación y despliegue

### DB-01 — La guía de carga de BD se contradice y está atrasada

`01_esquema_shopify.sql` contiene 49 tablas y 62 FKs en la definición V1; V2–V12 llevan el conjunto documentado a 54 tablas/79 FKs. Sin embargo, `07-datos-prueba.md:126-147` instruye ejecutar solo `01_esquema_shopify.sql` y luego espera los recuentos posteriores a V12, mientras `09-recorrido-guiado.md:27-29` correctamente indica ejecutar V2–V12. El repo llega a V18, que agrega 4 tablas y 5 FKs más; la BD local observada concuerda con 58 tablas de aplicación/84 FKs.

Otros síntomas de documentación vieja: `shopify_db/docs/README.md:19-30` dice “ocho documentos” pero enumera diez; `10-requerimientos.md:4` sigue describiendo la línea V1–V12 pese a incluir requerimientos V18/V19; no existe V19 en `shopify_db/database/migrations/`.

`R__roles_base.sql` inserta roles y aparece aplicada en Flyway. Aclarar si es dato de referencia requerido en producción o seed; `AGENTS.md:46` dice que los seeds deben permanecer fuera de migraciones productivas.

### REL-01 — El empaquetado/deploy no prueba que Flyway vea V2+

`application.properties:8-12` carga V1 del classpath y las demás migraciones desde `filesystem:../shopify_db/database/migrations`; el propio comentario advierte que hay que ajustar/copiar esa ruta si Render usa otro directorio de trabajo. `application-prod.properties:1-4` pasa `DATABASE_URL` directamente a `spring.datasource.url`; esa propiedad debe recibir una URL JDBC como `jdbc:postgresql://...`, no una URI `postgresql://...` sin transformación ([documentación Spring Boot](https://docs.spring.io/spring-boot/reference/data/sql.html)). No se encontró manifiesto Render/Docker que demuestre el directorio de trabajo, empaquetado de migraciones, valor JDBC, CORS o secretos de producción.

No se afirma que la conexión de producción falle: no hay entorno de producción disponible para probarla. Antes de desplegar, empaquetar migraciones V2–V18 en el artefacto o fijar el filesystem path y ejecutar un arranque limpio contra una BD desechable con `flyway validate`.

## Matriz resumida por dominio

| Dominio / requerimientos | Estado observado |
|---|---|
| BD/Flyway | **Conforme en la BD local consultada** hasta V18 + repetible; `0 FKs` no aplica a esta instancia. Procedimiento/documentación local no está sincronizado. Producción no verificada. |
| Auth/TOTP/JWT/cookies — RF-AUTH-01..07 | **Parcial**: registro, TOTP, refresh hash, correo y limpieza existen; lockout/revocación pueden revertirse, reset no revoca sesiones, CORS `/auth` falta y modo log puede exponer tokens. |
| Roles/aislamiento — RF-AUTH-08, RF-TENANT-01..03 | **No conforme / bloqueo crítico**: falta autorización admin por rol, superadmin alcanza tiendas, falta consistencia header/path y bypass de tienda congelada. |
| Storefront/catalog — RF-STORE-01, RF-ADMIN-01 | **No conforme**: no hay frontend ni GET público de productos/detalle; administración de catálogo es parcial. |
| Carrito/checkout/pedidos — RF-CART-01..03, RF-ORDER-01..03 | **Parcial**: conversión/reserva existe, pero carrito anónimo está bloqueado por autenticación global, confirmación no revalida tienda congelada, cancelación pagada no reembolsa y PII de checkout no se anonimiza. |
| Pagos/billing — RF-PAY-01..03, RF-BILL-01..03 | **No listo para dinero real**: modo live eludible con llave bajo modo test; intentos concurrentes/refunds inconsistentes; suscripción externa sin flujo completo de cobro/cancelación; alcance de V19 contradice decisiones vigentes. |
| Inventario/envíos/descuentos — RF-ADMIN-02, RF-SHIP, RF-DISC | **Parcial**: reservas para checkout, pero sin operaciones admin de inventario ni consumo al despacho; tracking público y no ordenado; descuento único y uso no revertido al cancelar. |
| Cuenta, wishlist, reseñas, GDPR — RF-STORE-02, RF-REV, RF-LEG | **Parcial**: endpoints de cuenta básicos; wishlist solo agrega, reseñas se crean pero no se moderan/leen por API, faltan datos de checkout en anonimización. |
| Notificaciones/WebSocket — RF-ADMIN-05 | **Parcial**: bandeja/WS existen; endpoints REST también quedan expuestos al bypass de RBAC. |
| Postman/tests/entrega | **Parcial/no reproducible end-to-end**: rutas declaradas ausentes, ejemplos estáticos, token save condicionado al TOTP y tests no ejecutados en este informe. |

## Orden recomendado de corrección

1. Revocar la credencial de `opencode.json` si es real; sustituirla por configuración fuera del repo.
2. Cerrar RBAC y tenant: bloquear `/api/admin` a clientes; negar admin/storefront/checkout a `super_admin`; validar rol del actor al resetear TOTP; unificar tenant de header/path y hacer obligatoria la política de tienda congelada.
3. Enforzar test-only por prefijo/llave para Stripe de tienda y plataforma; no exponer `MockAdapter`/webhooks de prueba fuera del perfil permitido.
4. Autenticar tracking; hacer lockout y revocación durables; invalidar refresh tras reset de TOTP.
5. Arreglar saldos de intents/refunds, idempotencia y estados: no marcar reembolsado antes del webhook; reembolsar cuando se cancela pedido pagado; reconciliar stock en despacho/entrega.
6. Completar API storefront, endpoints de inventario/catálogo/cuenta y construir el SPA único decidido.
7. Rehacer colección Postman como flujo con variables/responses dinámicas, `X-Carrito-Token`, asserts `pm.test` y pruebas negativas de roles/tenant; usar una BD de pruebas aislada.
8. Sincronizar README/guías/ADR/requerimientos y verificar arranque/despliegue Render + Supabase con el artefacto Flyway final.

**Conclusión sobre el bloqueo de FKs:** no arreglar ni recrear la BD `shopify_db_demo` por el reporte de “0 FKs”; la instancia local consultada tiene 84 FKs validadas y migraciones completas hasta V18. Si el usuario vio otra BD, identificar primero conexión, base y esquema. **Sí hay otros bloqueos de seguridad y funcionalidad que deben resolverse antes de exponer el catálogo o pagos.**
