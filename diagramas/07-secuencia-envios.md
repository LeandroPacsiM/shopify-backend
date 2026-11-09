# 07 — Secuencia envíos + tracking (base: RF-SHIP, V4/V6)

## Crear envío parcial

```mermaid
sequenceDiagram
    actor O as Operador tienda
    participant C as EnvioController
    participant S as EnvioService
    participant D as PostgreSQL

    O->>C: POST /api/admin/pedidos/:id/envios (líneas + cantidades)
    C->>S: valida misma tienda y pedido pagado/en_preparación; suma por línea ≤ comprado (lock)
    S->>D: TX: envío + items (UNIQUE envío/línea V6) + consume reserva a salida + historial pedido
    C-->>O: 201 envío con tracking
```

## Tracking del transportista

```mermaid
sequenceDiagram
    participant T as Transportista
    participant W as TrackingController
    participant S as EnvioService
    participant D as PostgreSQL

    T->>W: evento + X-Tracking-Timestamp + X-Tracking-Signature
    W->>W: HMAC-SHA256 del timestamp + body exacto; ventana ±5 min
    W->>S: valida payload, localiza envío con lock, deduplica por ref
    S->>D: TX: guarda envio_eventos; si ocurrido_en es anterior al último, conserva estado
    S->>D: si hay mapeo y está al día, actualiza estado; si todo entregado → pedido entregado + historial
```

Notas:
- Una línea en varios envíos sí; duplicada en el mismo, no (V6).
- Ubicación con stock no se elimina (RESTRICT V4): se desactiva.
- El despacho exige pedido pagado/en_preparación/enviado y consume la reserva a salida; sin reserva no hay envío.
- El tracking guarda el evento atrasado como auditoría pero no retrocede el estado; el pedido sube a enviado al despachar y a entregado solo con cobertura total.
- `TRACKING_WEBHOOK_SECRET` debe configurarse fuera del repo y tener al menos 32 bytes UTF-8; sin secreto el webhook falla cerrado.
- `X-Tracking-Timestamp` usa segundos Unix y `X-Tracking-Signature` contiene el HMAC en hexadecimal de `timestamp + "." + body UTF-8`. La referencia externa es obligatoria y se deduplica bajo lock.
