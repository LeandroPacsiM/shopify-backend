# 05 — Secuencia carrito → checkout → pedido (base: RF-CART/ORDER/TENANT, V5/V6/V9/V11)

```mermaid
sequenceDiagram
    actor C as Cliente
    participant S as StorefrontController
    participant K as CheckoutController
    participant Srv as Services
    participant D as PostgreSQL

    C->>S: POST /api/storefront/:slug/carrito/lineas (token anónimo)
    S->>Srv: CarritoService.agregar (valida variante activa + tienda del slug)
    Srv->>D: upsert línea (UNIQUE carrito+variante)
    S-->>C: 200 carrito

    C->>K: POST /api/checkout/sesiones (JWT cliente + 2FA)
    K->>Srv: CheckoutService.crear (exige cliente; carrito→cliente; misma tienda o 422)
    Srv->>D: congela subtotal/impuestos/envío/descuentos/total
    K-->>C: 201 checkout pendiente

    C->>K: POST /api/checkout/:id/confirmar
    K->>Srv: PedidoService.confirmar()
    Srv->>D: TX: lock carrito+niveles; valida stock; crea pedido + items snapshot + direcciones + impuestos (V9); reserva stock + movimiento; checkout completado; carrito convertido; historial
    K-->>C: 201 pedido pendiente de pago
```

Notas:
- Sin guest (V5): checkout y pedido exigen `cliente_id`; el carrito sí puede ser anónimo.
- Coherencia tenant (RF-TENANT): pedido, cliente, checkout, dirección, descuento y método de la misma `tienda_id`; si no, 422 sin cambios parciales.
- Totales se recalculan en servidor; el navegador nunca es fuente de verdad.
