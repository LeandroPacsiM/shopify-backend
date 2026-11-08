# ADR-009: Multi-tenant multi-sucursal (V10–V12)

## Estado

Aceptado.

## Fecha

2026-11-07.

## Contexto

El alcance pasó de tienda única a plataforma SaaS multi-tienda y multi-sucursal, verificado contra el modelo Shopify (sharding por `shop_id`, locations con inventario independiente, gateways configurados por tienda, sin cruce de datos entre tiendas). El V1 no tenía entidad `tiendas`.

## Decisión

- V10: `tiendas` + `tienda_pasarelas` (credenciales cifradas por tienda, modo test/vivo).
- V11: `tienda_id` NOT NULL + `RESTRICT` en 9 raíces, con backfill a la tienda demo; el resto hereda por join.
- V12: `tienda_usuarios` (un rol `admin`/`operador` por tienda); `super_admin` global en `usuario_roles`; `cliente` deriva de `clientes`.
- La baja de tienda es desactivar (`activa = FALSE`), nunca DELETE con recursos.

## Alternativas consideradas

- `tienda_id` en todas las tablas: rechazado; doble fuente de verdad, se deriva por join.
- Esquema/BD por tenant: rechazado; una sola BD simplifica consistencia y despliegue (sin pods a nuestra escala).

## Consecuencias

### Positivas

- Aislamiento por tenant impuesto en BD + índices por tienda para las consultas filtradas.

### Negativas

- El backend debe derivar `tienda_id` de la identidad y validar coherencia cruzada (pedido y cliente de la misma tienda), con tests anti-fuga.
- Seed, diagramas y namespaces de API deben volverse multi-tenant.
