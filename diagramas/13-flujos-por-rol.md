# 13 — Flujos punta a punta por rol (ADR-016 aplicado en F4)

Un diagrama por actor con endpoints reales. Guards: JWT staff (`aud=tienda-admin`)
vs CUSTOMER (`aud=tienda-storefront`); tenant por slug/header (`12`).

## STORE_OWNER — de crear la tienda a transferirla

```mermaid
flowchart TD
    R["POST /auth/registro-administrativo<br/>identidad sin permisos"]
    R --> V["POST /auth/email/confirmar + /auth/totp/*<br/>verifica + 2FA"]
    V --> L["POST /auth/login + /auth/login/totp<br/>JWT staff"]
    L --> T["POST /auth/tiendas<br/>nace STORE_OWNER + suscripción pendiente"]
    T --> F["POST /api/admin/facturacion/suscribir<br/>activa checkout"]
    F --> C["POST /api/admin/productos<br/>+ colecciones, etiquetas, inventario"]
    C --> E["POST /api/admin/usuarios<br/>equipo ADMIN/OPERATOR"]
    E --> O["opera: pedidos, pagos, reembolsos,<br/>envíos, reseñas, notificaciones"]
    O --> X["PATCH /api/admin/tienda/propietario<br/>transfiere (solo owner)"]
```

## ADMIN / OPERATOR — operar la tienda (sin ser dueños)

```mermaid
flowchart TD
    A["miembro agregado por email<br/>POST /api/admin/usuarios"]
    A --> G["GET /api/admin/**<br/>ambos administran"]
    G --> D["ADMIN sí: equipo, pasarelas,<br/>facturación, reset 2FA"]
    G --> P["OPERATOR no: 403 en<br/>/usuarios, /pasarelas, /facturacion"]
```

## CUSTOMER — comprar con passwordless

```mermaid
flowchart TD
    S["POST /auth/cliente/solicitar<br/>código al email (perfil aún no existe)"]
    S --> V["POST /auth/cliente/verificar<br/>nace el perfil + JWT + refresh"]
    V --> K["POST /api/storefront/:slug/carrito/lineas<br/>token anónimo o asociado"]
    K --> O["POST /api/checkout/orden<br/>congela totales"]
    O --> C["POST /api/checkout/:id/confirmar<br/>pedido + reserva stock"]
    C --> P["POST /api/checkout/pedidos/:id/pagos<br/>intento de cobro"]
    P --> T["GET .../cuenta/pedidos/:id/tracking"]
    T --> R["POST .../cuenta/resenas + wishlist<br/>DELETE .../cuenta = anonimiza (GDPR)"]
```

## SUPER_ADMIN — plataforma, nunca tiendas por dentro

```mermaid
flowchart TD
    O["GET /api/ops/tiendas + /auditoria<br/>lista y agregados"]
    O --> M["PATCH .../suspender|reactivar<br/>compliance con motivo"]
    M --> W["PATCH .../tiendas/:id/propietario<br/>confirma OWNER (D5)"]
    W -.->|prohibido RF-TENANT-03| X["pedidos, CUSTOMERs,<br/>productos, credenciales"]
```

## Reglas cruzadas

- JWT CUSTOMER nunca abre `/api/admin`; JWT staff nunca compra en `/checkout`.
- Mismo email permitido en ambos mundos, incluso en la misma tienda, sin
  compartir credenciales, sesiones ni permisos.
- Nada se borra con historial: cancelar/archivar/desactivar/anonimizar.
