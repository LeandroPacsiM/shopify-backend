# 09 — Recorrido guiado: qué tenemos y cómo se cuenta una compra

> Solo hay base de datos (más docs y diagramas). Aun así, el esquema ya cuenta una historia completa: la de `DEMO-0001`, un pedido que nace, se paga, viaja y se reseña. Este doc la narra en 9 pasos, con qué tablas toca cada uno y cómo verlo en pgAdmin.

## Qué tenemos hoy

- V1 (`01_esquema_shopify.sql`) + migraciones V2–V12: **54 tablas, 79 FKs, 66 índices**, triggers de `actualizado_en`.
- Decisiones: 2FA TOTP para todos, sin guest checkout, mock de pagos primero, cliente obligatorio, nada se borra con historial (se cancela/archiva/desactiva).
- Falta: todo el backend y el frontend. La BD es el esqueleto; el músculo viene después.

## La historia de DEMO-0001 en 9 pasos

| # | Paso | Qué pasa | Tablas que se escriben |
|---|---|---|---|
| 1 | Registro + 2FA | Cliente se registra, escanea el QR, valida su primer código y recibe backup codes | `usuarios`, `clientes`, `usuario_roles`, `usuario_codigos_recuperacion` |
| 2 | Catálogo | El operador publica la camiseta con 2 variantes (M/Azul, L/Verde) y stock en el almacén | `productos`, `producto_opciones`, `producto_opcion_valores`, `producto_variantes`, `articulos_inventario`, `niveles_inventario`, `movimientos_inventario` |
| 3 | Carrito | El cliente mete 2×M + 1×L; el carrito es anónimo hasta el checkout | `carritos`, `carrito_items` |
| 4 | Checkout | Se identifica (login + TOTP), elige dirección y envío; el sistema congela subtotal, impuesto, envío, descuento y total | `checkout_sesiones` |
| 5 | Pedido | Se confirma: snapshot de líneas, direcciones e impuestos; checkout completado, carrito convertido | `pedidos`, `pedido_items`, `pedido_direcciones`, `pedido_impuestos`, `historial_estados_pedido` |
| 6 | Pago | Primer intento rechazado, segundo aprobado; cada webhook queda registrado con su event-id (sin duplicados gracias a V3) | `pagos`, `pago_eventos` |
| 7 | Envío | Sale del almacén con tracking; cada evento del transportista se guarda y mapea al estado local | `envios`, `envio_items`, `envio_eventos` |
| 8 | Cierre | Pedido entregado y archivado (nunca borrado); si hay devolución, va a `reembolsos` contra el pago aprobado | `pedidos` (estado + archivado), `reembolsos` |
| 9 | Post-venta | El cliente deja 1 reseña (una por producto) y guarda favoritos | `resenas`, `listas_deseos` |

## Verlo en pgAdmin (10 minutos)

1. Crea la BD `shopify_db_demo` y ejecuta en orden: `01_esquema_shopify.sql` y luego `migrations/V2__...` hasta `V12__...`.
2. Copia el bloque de `07-datos-prueba.md` a `seed_demo.local.sql` y ejecútalo (ya es compatible con V2–V12, con 2 tiendas).
3. Corre estas comprobaciones y cuenta la historia con sus resultados:

```sql
-- La compra de un vistazo
SELECT numero_pedido, estado, total, archivado
FROM pedidos WHERE numero_pedido = 'DEMO-0001';

-- El snapshot: lo vendido no cambia aunque cambie el catálogo
SELECT producto_titulo, variante_titulo, cantidad, precio_total
FROM pedido_items WHERE pedido_id = (SELECT id FROM pedidos WHERE numero_pedido = 'DEMO-0001');

-- El dinero: intento fallido + aprobado, sin duplicados
SELECT estado, monto, transaccion_externa_id FROM pagos
WHERE pedido_id = (SELECT id FROM pedidos WHERE numero_pedido = 'DEMO-0001');

-- El viaje del paquete
SELECT estado, ocurrido_en FROM envio_eventos
WHERE envio_id = (SELECT id FROM envios WHERE numero_tracking = 'DEMO-TRACK-0001')
ORDER BY ocurrido_en;
```

Si las 4 devuelven filas coherentes (total 179.92, 2 líneas, 2 pagos, 4 eventos), tu BD cuenta la historia bien.
