# 12 — Autorización por superficie y tenant (ADR-016/017 aplicados)

- **Alcance:** autorización backend de roles para `/api/admin`, `/api/checkout`, Storefront y Ops.
- **Fuente de roles:** `usuario_roles` solo SUPER_ADMIN; `tienda_usuarios` con STORE_OWNER/ADMIN/OPERATOR; CUSTOMER fuera de `usuario_roles`, con JWT propio (`aud=tienda-storefront`) que nunca se intercambia con el staff.
- **Sesión limitada (ADR-017):** JWT staff con `mfa=pendiente` viaja como autoridad y solo lee; toda escritura bajo `/api/admin/**` es 403 aunque haya vínculo.
- **Catálogo/carrito:** la lectura del catálogo y las operaciones del carrito con token son públicas; eso no habilita checkout invitado. Las comprobaciones de tenant para requests con header y tienda congelada se endurecen en la tarea siguiente.

```mermaid
flowchart LR
    REQ[Request HTTP] --> JWT[JwtFilter: valida JWT y roles globales]
    JWT --> TEN[TenantFilter: slug de ruta Storefront o X-Tienda-Slug<br/>si llegan ambos, deben coincidir]
    TEN --> AUTHZ[AuthorizationManager: ruta + principal + TenantContext]

    AUTHZ -->|GET productos / carrito con token| PUBLIC[Storefront público]
    AUTHZ -->|/api/admin/**| STAFF{tienda_usuarios<br/>STORE_OWNER/ADMIN/OPERATOR<br/>en la tienda activa?}
    STAFF -->|sí, no SUPER_ADMIN| MFA{JWT `mfa=pendiente`<br/>y método no-GET?}
    MFA -->|sí| DENY
    MFA -->|no| ADMIN[Staff de esa tienda]
    STAFF -->|no / SUPER_ADMIN| DENY[403]

    AUTHZ -->|/api/checkout/**| CUSTOMER{JWT CUSTOMER<br/>tienda_id == tienda activa?}
    CUSTOMER -->|sí| CHECKOUT[Checkout como CUSTOMER]
    CUSTOMER -->|no / SUPER_ADMIN| DENY

    AUTHZ -->|/api/storefront/{slug}/cuenta/**| CUSTOMER
    AUTHZ -->|/api/ops/**| GLOBAL{usuario_roles incluye<br/>SUPER_ADMIN?}
    GLOBAL -->|sí| OPS[Ops global]
    GLOBAL -->|no| DENY
```

## Matriz de decisión

| Superficie | Autorización requerida | Rechazo explícito |
|---|---|---|
| `/api/admin/**` (escritura: POST/PATCH/PUT/DELETE) | JWT staff `mfa=completa` + `tienda_usuarios` del tenant activo | JWT `mfa=pendiente` (403 aunque sea OWNER: deny-list ADR-017); CUSTOMER; SUPER_ADMIN; tenant ausente |
| `/api/admin/**` (lectura: GET/HEAD) | `tienda_usuarios` del tenant activo (la limitada trial también lee) | CUSTOMER sin vínculo staff; SUPER_ADMIN; tenant ausente |
| `/api/admin/usuarios/**`, `/api/admin/pasarelas/**`, `/api/admin/facturacion/**` | STORE_OWNER/ADMIN de la tienda activa (con resguardo y transferencia del OWNER) | OPERATOR; CUSTOMER; SUPER_ADMIN; tenant ausente |
| `/api/checkout/**` | JWT CUSTOMER con `tienda_id` del tenant activo | Cualquier JWT staff/SUPER_ADMIN; tenant ausente |
| `/api/storefront/{slug}/cuenta/**` | JWT CUSTOMER del tenant activo | Cualquier JWT staff/SUPER_ADMIN; tenant ausente |
| `GET /api/storefront/{slug}/productos/**`, `/api/storefront/{slug}/carrito/**` | Público; el carrito exige token opaco para mutaciones | La autorización pública no permite comprar ni confirmar checkout |
| `/api/ops/**` | Rol global SUPER_ADMIN | CUSTOMER, staff de tienda |

La autorización de ruta corre después de autenticar y resolver el tenant. La pertenencia a una tienda por sí sola no concede permiso para usar cualquier superficie; se consulta el tipo de rol adecuado para esa superficie.

## Resolución del tenant y checkout

- Storefront resuelve la tienda desde `/api/storefront/{slug}/...`; si se envía `X-Tienda-Slug`, debe ser exactamente el mismo slug.
- Admin y checkout exigen `X-Tienda-Slug`; la tienda congelada bloquea ambos y Storefront salvo `/api/admin/facturacion`.
- Storefront solo expone las rutas públicas de catálogo/carrito y las privadas de cuenta autorizadas explícitamente; las demás se deniegan.
- Crear y confirmar checkout validan que checkout, CUSTOMER, carrito y variantes pertenezcan a la tienda activa; el carrito asociado debe pertenecer al CUSTOMER autenticado.
- Las rutas públicas de carrito aceptan el token opaco, pero nunca permiten usarlo para recuperar o modificar un carrito de otra tienda ni el carrito de otro CUSTOMER autenticado.
