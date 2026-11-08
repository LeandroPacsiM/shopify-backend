# 02 — Casos de uso por actor (mapa; el detalle vive en `10-requerimientos.md`)

## SuperAdmin (dueño plataforma)

```mermaid
flowchart LR
    SUP["SuperAdmin"] --> O1["RF-OPS-01/02<br/>tenants + compliance"]
    SUP --> O2["RF-OPS-03<br/>negocio sin código"]
    SUP --> O3["RF-OPS-04<br/>plataforma"]
    SUP --> O4["RF-AUTH-06<br/>reset 2FA"]
    SUP -.->|prohibido RF-TENANT-03| X["datos internos<br/>de tiendas"]
```

## Admin / Operador (de su tienda)

```mermaid
flowchart LR
    ADM["Admin / Operador"] --> A1["RF-ADMIN-01<br/>catálogo"]
    ADM --> A2["RF-ADMIN-02<br/>inventario"]
    ADM --> A3["RF-ADMIN-03<br/>pedidos/pagos/envíos"]
    ADM --> A4["RF-DISC + RF-REV<br/>cupones y reseñas"]
    ADM -.->|prohibido| Y["otra tienda · checkout"]
```

## Cliente (de una tienda)

```mermaid
flowchart LR
    CLI["Cliente"] --> C1["RF-AUTH-01/02<br/>registro + TOTP"]
    CLI --> C2["RF-STORE + RF-CART<br/>tienda y carrito"]
    CLI --> C3["RF-CART-02/03 + RF-PAY<br/>checkout y pago"]
    CLI --> C4["RF-ORDER + RF-SHIP<br/>pedidos y tracking"]
    CLI --> C5["RF-REV<br/>reseñas y wishlist"]
    CLI -.->|prohibido| Z["/admin · otra tienda"]
```

## Secuencia propuesta de diagramas

- `02` casos de uso (este) → `03` clases de dominio → `04` secuencia auth+2FA → `05` secuencia carrito/checkout/pedido → `06` secuencia pagos+webhooks → `07` secuencia envíos → `08` componentes backend.
