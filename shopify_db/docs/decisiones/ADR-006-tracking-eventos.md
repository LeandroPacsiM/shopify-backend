# ADR-006: Envíos y tracking mediante eventos

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

Un pedido puede prepararse, salir del almacén, entrar en tránsito, acercarse al destino y entregarse en momentos distintos. También puede dividirse en varios envíos o incluir solo parte de sus líneas. Guardar únicamente el último estado o un número de tracking en el pedido eliminaría la cronología y dificultaría explicar demoras, devoluciones o notificaciones desordenadas.

Los transportistas mantienen sus propios sistemas y publican estados mediante APIs, webhooks o consultas periódicas. Esos proveedores no son bases de datos internas del comercio. La base local necesita representar envíos y conservar los eventos recibidos, mientras la aplicación se ocupa de autenticación, normalización, reintentos y deduplicación.

## Decisión

Se utiliza `envios` como entidad de cabecera y `envio_eventos` como historial temporal, complementadas por `transportistas` y `envio_items`.

- `transportistas` conserva identidad, código, URL general de tracking y habilitación.
- `envios` relaciona pedido, transportista opcional y ubicación de origen; guarda tracking, URL, estado actual y fechas de salida y entrega.
- `envio_items` indica qué líneas y cantidades contiene cada envío, permitiendo representar envíos parciales o múltiples.
- `envio_eventos` conserva estado, descripción, ubicación, `ocurrido_en`, referencia externa y fecha local de registro.

Los datos operativos de transportistas se obtienen de **APIs o webhooks externos** mediante adaptadores. No se crea una base por transportista. El estado actual de `envios` es un resumen; `envio_eventos` conserva el recorrido. La aplicación debe mapear estados externos, deduplicar notificaciones, resolver eventos fuera de orden y mantener sondeo de respaldo cuando el proveedor no garantice entrega de webhooks.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| Guardar solo tracking y estado en `pedidos` | Modelo pequeño y consulta inmediata. | No representa varios paquetes, líneas parciales ni historial cronológico. | Mezcla el pedido con un proceso logístico que puede tener múltiples envíos. |
| Sobrescribir el estado actual sin eventos | Reduce almacenamiento y lógica de consulta. | Impide auditar el recorrido y diagnosticar eventos atrasados o contradictorios. | El seguimiento requiere historia, no solamente el último valor conocido. |
| Una base de datos por transportista | Aísla datos y formatos de cada proveedor. | Fragmenta las relaciones con pedidos y multiplica la operación sin eliminar la necesidad de APIs. | El transportista es externo y el comercio necesita una representación local uniforme. |
| Consumir la API del transportista en cada consulta sin persistir eventos | Muestra información potencialmente actualizada y usa menos almacenamiento local. | Depende de disponibilidad, límites de velocidad y retención del proveedor; dificulta historial propio. | La operación y la experiencia de usuario no deben depender siempre de una llamada externa en tiempo real. |

## Consecuencias

### Positivas

- El modelo admite varios envíos por pedido y cantidades específicas por línea.
- `ocurrido_en` y `creado_en` distinguen el momento del evento de su registro local.
- El historial permite mostrar seguimiento, investigar incidencias y reconstruir el estado conocido.
- Los transportistas pueden cambiar su API sin alterar el significado relacional común de los envíos.
- La consulta del tracking no exige llamar al proveedor en cada visualización.

### Negativas

- Los datos locales pueden quedar desactualizados respecto del transportista y requieren conciliación.
- `numero_tracking` tiene índice pero no unicidad; los identificadores duplicados o reutilizados necesitan una regla de ámbito.
- `envio_items` no impide repetir una pareja envío/línea, mezclar líneas de otro pedido ni superar la cantidad comprada.
- No existen checks de cronología ni una restricción de deduplicación para `referencia_externa`; la integración debe controlarlos.
- Los eventos desconocidos, desordenados o repetidos exigen adaptadores, reintentos, observabilidad y pruebas de contrato.
