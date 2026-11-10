# 14 — Recorrido demo STORE_OWNER (todo lo construido, ADR-016/017)

Un solo diagrama para exponer: del registro a la tienda vendiendo (trial),
con endpoints reales y guards. Demo actual: Postman (78) + SPA staff.

```mermaid
flowchart TD
    R["1. POST /auth/registro-administrativo<br/>identidad sin permisos ni rol"]
    R --> V["2. POST /auth/email/confirmar<br/>código por correo (Brevo)"]
    V --> L["3. POST /auth/login → challenge<br/>+ POST /auth/login/totp sin código<br/>JWT limitado mfa=pendiente"]
    L --> T["4. POST /auth/tiendas<br/>nace STORE_OWNER + suscripción pendiente"]
    T --> M["5. GET /auth/tiendas<br/>mis tiendas (trial y completo)"]
    M --> Q["trial: GETs sí, escritura /api/admin = 403<br/>(deny-list ADR-017)"]
    Q --> W["6. POST /auth/totp/* → /auth/login/totp<br/>JWT completo mfa=completa"]
    W --> F["7. POST /api/admin/facturacion/suscribir<br/>mock: activa checkout (Stripe Billing futuro)"]
    F --> C["8. POST /api/admin/productos (borrador)<br/>→ PATCH publicar → POST variantes<br/>GET ?estado=borrador|activo"]
    C --> S["9. Ubicación + movimiento entrada<br/>stock para la variante"]
    S --> E["10. POST /api/admin/usuarios<br/>equipo ADMIN/OPERATOR por email"]
    E --> D["11. María (CUSTOMER, mismo email vale)<br/>código → carrito → SIN compra (demo)"]
```

```mermaid
flowchart LR
    subgraph JWTs["JWTs que nunca se cruzan"]
        A["staff aud=tienda-admin<br/>mfa pendiente→completa"]
        B["CUSTOMER aud=tienda-storefront<br/>passwordless por código"]
    end
    subgraph Gates["Gates"]
        G1["2FA: suscribir, pagos,<br/>equipo, transferir, pasarelas"]
        G2["Suscripción activa:<br/>checkout nuevo"]
        G3["Stock: confirmar pedido"]
    end
    A --> G1
    G1 --> G2
    B --> G2
    G2 --> G3
```

## Guion demo (15 min)

1. Registro → código Brevo → trial → crear `tienda-paz` (Postman Auth o SPA).
2. Probar el 403 escribiendo con limitada; enrolar 2FA inline; insignia Completa.
3. `GET /auth/tiendas` (la lista), suscribir mock, crear + publicar Gorra + variante + stock.
4. Agregar admin/operador por email (el invitado ya registrado).
5. María con el mismo email: código → carrito con 2 Gorras → ver carrito (sin comprar).

## Lo que NO entra en esta demo

- Cobro Stripe test (pasarela + `listen` + confirmar PI) ni Billing real: camino verificado, fuera del guion.
- Invitación por correo al equipo: el agregar es directo (decisión 2026-11-09).
- Borrado de tiendas con owner: V22 lo impide en una TX (hallazgo registrado).
