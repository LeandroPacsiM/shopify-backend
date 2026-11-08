# ADR-004: Snapshot histórico en las líneas de pedido

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

El título, el SKU y el precio de una variante pueden cambiar después de una compra. Incluso la variante puede desactivarse o eliminarse. Si una línea de pedido se reconstruyera siempre mediante joins al catálogo actual, una orden antigua mostraría datos y precios diferentes de los aceptados por el comprador.

El pedido necesita conservar tanto la relación con la variante de origen, cuando siga disponible, como la descripción comercial y los importes aplicados en el momento de la venta. Esta información histórica también es necesaria para envíos, atención al cliente, reembolsos y reportes.

## Decisión

`pedido_items` se define como un **snapshot histórico de cada línea vendida**. La línea almacena `producto_titulo`, `variante_titulo`, `sku`, `cantidad`, `precio_unitario` y `precio_total` en el momento de crear el pedido.

La columna `variante_id` se conserva como referencia opcional al catálogo. Su regla `ON DELETE SET NULL` permite mantener la línea histórica aunque se elimine la variante. Los cambios posteriores de título, SKU o precio en `productos` o `producto_variantes` no deben reescribir el snapshot.

Al crear el pedido, la aplicación copia los valores confirmados y calcula los importes en la misma operación transaccional. Los datos actuales del catálogo no son una fuente sustituta para reconstruir una venta histórica.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| Consultar siempre el producto y la variante actuales | No duplica títulos, SKU ni precios y mantiene normalización. | Una venta anterior cambia cuando se edita o elimina el catálogo y puede perder el precio acordado. | No proporciona un historial comercial estable. |
| Guardar solamente un documento JSON de la línea | Captura una estructura flexible y potencialmente completa. | Dificulta constraints, consultas, sumas y tipado de columnas usadas habitualmente. | Los campos esenciales del snapshot son estables y conviene consultarlos como columnas. |
| Impedir cualquier cambio o borrado del catálogo | Las referencias originales siempre conservarían sus valores. | Bloquea correcciones, evolución del catálogo y descontinuación de variantes. | La operación del catálogo no debe quedar congelada por todas las ventas históricas. |
| Crear una versión completa de producto para cada pedido | Mantiene versiones navegables del catálogo. | Multiplica entidades y relaciones, y obliga a diseñar un sistema general de versionado. | Es una solución desproporcionada para los datos históricos requeridos por una línea de venta. |

## Consecuencias

### Positivas

- Los pedidos conservan el texto, SKU y precios realmente aceptados al comprar.
- La eliminación de una variante no destruye la información de la línea gracias a `variante_id` opcional y `SET NULL`.
- Los reportes de ventas no dependen de los precios vigentes del catálogo.
- Envíos y reembolsos pueden mostrar la descripción histórica aunque el producto ya no exista.
- Las columnas tipadas permiten sumar cantidades e importes sin interpretar JSON.

### Negativas

- Se duplican deliberadamente datos del catálogo y aumenta el almacenamiento de cada línea.
- El snapshot puede ser incorrecto desde el inicio si la aplicación copia valores distintos de los confirmados.
- No existe un check que garantice `precio_total = cantidad * precio_unitario`; la coherencia debe calcularse y probarse.
- Las correcciones legítimas requieren un procedimiento explícito, ya que no hay sincronización automática con el catálogo.
- Las consultas que necesiten datos actuales e históricos deben distinguir claramente el join a `producto_variantes` de las columnas snapshot.
