# 12 — Autorización por superficie y tenant

- **Alcance:** autorización backend de roles para `/api/admin`, `/api/checkout`, Storefront y Ops.
- **Fuente de roles:** `usuario_roles` contiene roles globales de plataforma; `tienda_usuarios` contiene `admin`/`operador` por tienda; `clientes` identifica al cliente de su tienda (V12).
- **Catálogo/carrito:** la lectura del catálogo y las operaciones del carrito con token son públicas; eso no habilita checkout invitado. Las comprobaciones de tenant para requests con header y tienda congelada se endurecen en la tarea siguiente.

```mermaid
flowchart LR
    REQ[Request HTTP] --> JWT[JwtFilter: valida JWT y roles globales]
    JWT --> TEN[TenantFilter: slug de ruta Storefront o X-Tienda-Slug<br/>si llegan ambos, deben coincidir]
    TEN --> AUTHZ[AuthorizationManager: ruta + principal + TenantContext]

    AUTHZ -->|GET productos / carrito con token| PUBLIC[Storefront público]
    AUTHZ -->|/api/admin/**| STAFF{tienda_usuarios<br/>rol admin u operador<br/>en la tienda activa?}
    STAFF -->|sí, no super_admin| ADMIN[Admin de esa tienda]
    STAFF -->|no / super_admin| DENY[403]

    AUTHZ -->|/api/checkout/**| CUSTOMER{clientes.tienda_id<br/>coincide con tienda activa<br/>y no es staff de ella?}
    CUSTOMER -->|sí| CHECKOUT[Checkout como cliente]
    CUSTOMER -->|no / super_admin| DENY

    AUTHZ -->|/api/storefront/{slug}/cuenta/**| CUSTOMER
    AUTHZ -->|/api/ops/**| GLOBAL{usuario_roles incluye<br/>super_admin?}
    GLOBAL -->|sí| OPS[Ops global]
    GLOBAL -->|no| DENY
```

## Matriz de decisión

| Superficie | Autorización requerida | Rechazo explícito |
|---|---|---|
| `/api/admin/**` | `tienda_usuarios` del tenant activo con rol `admin` u `operador` | Cliente sin rol de staff; `super_admin`; tenant ausente |
| `/api/admin/usuarios/**`, `/api/admin/pasarelas/**`, `/api/admin/facturacion/**` | Admin de la tienda activa | Operador; cliente; `super_admin`; tenant ausente |
| `/api/checkout/**` | `ROLE_cliente` y fila `clientes` del principal en el tenant activo | Cualquier autoridad admin/operador/super_admin; tenant ausente |
| `/api/storefront/{slug}/cuenta/**` | `ROLE_cliente` y cliente perteneciente al tenant activo | Cualquier autoridad admin/operador/super_admin; tenant ausente |
| `GET /api/storefront/{slug}/productos/**`, `/api/storefront/{slug}/carrito/**` | Público; el carrito exige token opaco para mutaciones | La autorización pública no permite comprar ni confirmar checkout |
| `/api/ops/**` | Rol global `super_admin` | Cliente, admin u operador de tienda |

La autorización de ruta corre después de autenticar y resolver el tenant. La pertenencia a una tienda por sí sola no concede permiso para usar cualquier superficie; se consulta el tipo de rol adecuado para esa superficie.

## Resolución del tenant y checkout

- Storefront resuelve la tienda desde `/api/storefront/{slug}/...`; si se envía `X-Tienda-Slug`, debe ser exactamente el mismo slug.
- Admin y checkout exigen `X-Tienda-Slug`; la tienda congelada bloquea ambos y Storefront salvo `/api/admin/facturacion`.
- Storefront solo expone las rutas públicas de catálogo/carrito y las privadas de cuenta autorizadas explícitamente; las demás se deniegan.
- Crear y confirmar checkout validan que checkout, cliente, carrito y variantes pertenezcan a la tienda activa; el carrito asociado debe pertenecer al cliente autenticado.
- Las rutas públicas de carrito aceptan el token opaco, pero nunca permiten usarlo para recuperar o modificar un carrito de otra tienda ni el carrito de otro cliente autenticado.
