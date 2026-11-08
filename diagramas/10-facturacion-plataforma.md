# 10 — Facturación de plataforma (base: RF-BILL, V18)

```mermaid
sequenceDiagram
    actor A as Admin tienda
    participant C as AdminFacturacionController
    participant S as FacturacionService
    participant W as Stripe (V19)
    participant D as PostgreSQL

    A->>C: POST /api/admin/facturacion/suscribir (plan)
    C->>S: suscribir() → pendiente (una por tienda)
    Note over S,W: V19 crea customer + subscription con price
    W->>S: POST /api/webhooks/facturacion (firma + event-id)
    S->>D: TX: dedup + activa + periodo_fin + descongela
    A->>C: GET /api/admin/facturacion (plan, estado, periodo)
```

```mermaid
flowchart LR
    SUB["sin suscripción vigente"] --> BLK["crear checkout → 409"]
    VEN["webhook suscripcion.vencida"] --> FRZ["tienda.activa=FALSE"]
    PAG["webhook suscripcion.pagada"] --> UNF["activa + periodo + descongela"]
```

- Suscripción POR `tienda_id` (una activa; mensual 30d / anual 365d), con nuestras llaves —no las de la tienda (decisión Billing Shopify en `AGENTS.md`).
- Dinero del comprador nunca a nuestra cuenta: cada tienda cobra en SU pasarela (V10).
- Impago = congelar (`activa=FALSE`: sin checkout nuevo), nunca DELETE con historial.
- Cobro Stripe real V19: customer + subscription con price del plan al suscribir (llaves de plataforma por entorno); `invoice.payment_succeeded` renueva, `customer.subscription.deleted` cancela y congela; sin configurar queda pendiente (verificado con stub).