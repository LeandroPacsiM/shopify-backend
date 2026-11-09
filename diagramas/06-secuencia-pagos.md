# 06 — Secuencia pagos + webhooks + reembolsos (base: RF-PAY, V3, V10)

## Cobro con adapter por tienda

```mermaid
sequenceDiagram
    actor C as Cliente
    participant K as CheckoutController
    participant P as PagoService
    participant A as PaymentAdapter (mock / stripe test)
    participant D as PostgreSQL

    C->>K: POST /api/checkout/:id/pagos (pasarela + idempotencia)
    K->>P: crearIntento (bloquea pedido y reserva saldo)
    P->>D: lee pagos aprobados + pendientes/procesando
    P->>A: autorizar(saldo restante, moneda, idempotency-key)
    A-->>P: transaccion_externa_id (mock: ficticio; stripe: pi_...)
    P->>D: TX: pago procesando + evento; confirma bajo lock del pedido
    K-->>C: pago procesando
```

## Webhook idempotente

```mermaid
sequenceDiagram
    participant G as Pasarela
    participant W as WebhookController
    participant P as PagoService
    participant D as PostgreSQL

    G->>W: POST /api/webhooks/pagos/:pasarela (firma + event-id)
    W->>P: verifica firma y localiza pago
    P->>D: TX: si event-id ya existe (V3) → 200 sin cambios; si no, inserta evento + actualiza pago + historial pedido (+pedido pagado si cubre total)
```

## Webhook crudo Stripe (traductor, cierra ADR-013)

```mermaid
sequenceDiagram
    participant S as Stripe
    participant W as WebhookController
    participant P as PagoService
    participant D as PostgreSQL

    S->>W: POST /api/webhooks/pagos/stripe (JSON crudo + Stripe-Signature)
    W->>P: webhookStripe(): parsea, mapea tipo, verifica monto
    P->>D: TX: payment_intent.* → flujo idempotente existente; refund.* → confirma reembolso; tipo raro → 200 sin cambios
```

Notas:
- Tipos: `succeeded/failed/canceled` → aprobado/rechazado/cancelado; `refund.succeeded/updated/failed` → reembolso aprobado/rechazado; resto se ignora (Stripe reintenta lo no-2xx).
- El monto del evento se coteja en céntimos contra pago/reembolso (409 si difiere).

## Reembolso parcial

```mermaid
sequenceDiagram
    actor A as Admin tienda
    participant C as AdminPagoController
    participant P as PagoService
    participant Ad as PaymentAdapter
    participant D as PostgreSQL

    A->>C: POST /api/admin/pagos/:id/reembolsos (monto, motivo, Idempotency-Key)
    C->>P: valida tenant y clave idempotente
    P->>D: TX corta: lock pedido/pago; reserva saldo y persiste reembolso pendiente + clave
    P->>Ad: reembolsar (credenciales + misma clave idempotente)
    Ad-->>P: refund externo + procesando
    P->>D: TX corta: guarda referencia externa y estado procesando
    Ad->>C: webhook firmado refund.succeeded / refund.failed
    C->>P: valida firma, monto y event-id
    P->>D: TX: aprueba/rechaza reembolso; solo al cubrir el total actualiza pago/pedido
```

Notas:
- `transaccion_externa_id` único por pasarela/pago (V3); event-id ≠ transaction-id.
- El saldo de cobro reserva pagos `pendiente` y `procesando`; dos intentos no pueden comprometer el total a la vez.
- El reembolso reserva en BD antes de la llamada externa; la clave se guarda como evento de pago y el adaptador la reusa para Stripe.
- El saldo reembolsable incluye reembolsos pendientes/procesando/aprobados; los rechazados liberan saldo. No se marca el pago/pedido como reembolsado antes de la confirmación.
- Todo sigue en modo test; `vivo` y llaves `live` están bloqueadas.
