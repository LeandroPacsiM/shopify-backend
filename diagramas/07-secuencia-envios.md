# 07 — Secuencia envíos + tracking (base: RF-SHIP, V4/V6)

## Crear envío parcial

```mermaid
sequenceDiagram
    actor O as Operador tienda
    participant C as EnvioController
    participant S as EnvioService
    participant D as PostgreSQL

    O->>C: POST /api/admin/pedidos/:id/envios (líneas + cantidades)
    C->>S: valida misma tienda y pedido; suma por línea ≤ comprado (lock)
    S->>D: TX: envío + items (UNIQUE envío/línea V6) + historial pedido
    C-->>O: 201 envío con tracking
```

## Tracking del transportista

```mermaid
sequenceDiagram
    participant T as Transportista
    participant W as TrackingController
    participant S as EnvioService
    participant D as PostgreSQL

    T->>W: evento (tracking + estado externo + ocurrido_en + ref)
    W->>S: localiza envío, deduplica por ref
    S->>D: TX: guarda envio_eventos; mapea a estado local permitido (si no hay mapeo, conserva anterior)
    S->>D: si todo entregado → pedido entregado + historial
```

Notas:
- Una línea en varios envíos sí; duplicada en el mismo, no (V6).
- Ubicación con stock no se elimina (RESTRICT V4): se desactiva.
- Eventos atrasados se ordenan por `ocurrido_en`, no por llegada.
