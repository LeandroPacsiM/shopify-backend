# 02 — Casos de uso por actor (mapa; el detalle vive en `10-requerimientos.md`, canónicos en ADR-016)

## SUPER_ADMIN (plataforma)

```mermaid
flowchart LR
    SUP["SUPER_ADMIN"] --> O1["RF-OPS-01/02<br/>tenants + compliance"]
    SUP --> O2["RF-OPS-03<br/>negocio sin código"]
    SUP --> O3["RF-OPS-04<br/>plataforma"]
    SUP --> O4["RF-AUTH-06<br/>reset 2FA"]
    SUP --> O5["Confirmar STORE_OWNER<br/>(plan v5)"]
    SUP -.->|prohibido RF-TENANT-03| X["datos internos<br/>de tiendas"]
```

## STORE_OWNER / ADMIN / OPERATOR (de su tienda)

```mermaid
flowchart LR
    ADM["STORE_OWNER / ADMIN / OPERATOR"] --> A1["RF-ADMIN-01<br/>catálogo"]
    ADM --> A2["RF-ADMIN-02<br/>inventario"]
    ADM --> A3["RF-ADMIN-03<br/>pedidos/pagos/envíos"]
    ADM --> A4["RF-DISC + RF-REV<br/>cupones y reseñas"]
    ADM -.->|prohibido| Y["otra tienda · checkout"]
```

## CUSTOMER (de una tienda)

```mermaid
flowchart LR
    CLI["CUSTOMER"] --> C1["RF-AUTH-10<br/>código por email (objetivo B)"]
    CLI --> C2["RF-STORE + RF-CART<br/>tienda y carrito"]
    CLI --> C3["RF-CART-02/03 + RF-PAY<br/>checkout y pago"]
    CLI --> C4["RF-ORDER + RF-SHIP<br/>pedidos y tracking"]
    CLI --> C5["RF-REV<br/>reseñas y wishlist"]
    CLI -.->|prohibido| Z["/admin · otra tienda"]
```

## Secuencia propuesta de diagramas

- `02` casos de uso (este) → `03` clases de dominio → `04` secuencia auth+2FA → `05` secuencia carrito/checkout/pedido → `06` secuencia pagos+webhooks → `07` secuencia envíos → `08` componentes backend.
