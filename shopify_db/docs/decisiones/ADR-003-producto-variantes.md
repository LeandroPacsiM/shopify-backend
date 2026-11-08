# ADR-003: Productos, variantes y opciones

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

Un producto comercial como una camiseta puede compartir título, descripción, proveedor e imágenes, pero venderse en distintas tallas y colores. Cada combinación comprable puede tener SKU, código de barras, precio, peso, estado y stock propios. Si todas esas características se almacenaran en una única fila de producto, habría que duplicar la información común o limitar el catálogo a una sola configuración.

También se necesita conocer qué opciones ofrece cada producto y qué valores componen cada variante. Carritos, líneas de pedido e inventario deben referirse a la versión exacta que compra o se almacena, no solo al producto genérico.

## Decisión

Se separa el concepto editorial de **producto** del concepto comprable de **variante** mediante las tablas `productos`, `producto_opciones`, `producto_opcion_valores`, `producto_variantes` y `variante_opcion_valores`.

- `productos` conserva título, slug, descripción, proveedor, publicación y estado general.
- `producto_opciones` define dimensiones como talla o color y su posición.
- `producto_opcion_valores` contiene los valores posibles de cada opción.
- `producto_variantes` contiene SKU, código de barras, precios, peso y disponibilidad de cada versión comprable.
- `variante_opcion_valores` relaciona una variante con los valores que la describen.
- `carrito_items`, `pedido_items` y `articulos_inventario` referencian la variante. Las imágenes pueden ser generales del producto o apuntar opcionalmente a una variante.

La aplicación debe validar que los valores asociados pertenezcan al mismo producto y que cada opción tenga como máximo un valor por variante, porque la restricción actual no garantiza esas dos reglas.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| Una sola tabla de productos con todos los atributos | Consultas iniciales simples y menos joins. | Duplica información común o impide representar varias combinaciones con precio y stock independientes. | No escala con naturalidad a talla, color y otras opciones comprables. |
| Un producto independiente por cada combinación | Cada fila tendría directamente su SKU, precio y stock. | Fragmenta el catálogo, las colecciones y las reseñas, y dificulta presentar una sola ficha comercial. | Confunde la identidad editorial del producto con sus versiones de venta. |
| Atributos completamente libres en JSON | Alta flexibilidad y pocas tablas de opciones. | Dificulta consultar combinaciones, reutilizar valores y garantizar referencias válidas. | Las opciones y variantes son parte central del dominio y merecen relaciones explícitas. |
| Variantes sin opciones normalizadas | Conserva SKU y precio por variante con menor complejidad. | No representa de forma fiable qué combinación de talla o color corresponde a cada variante. | La descripción libre de una variante no sustituye la configuración estructurada. |

## Consecuencias

### Positivas

- Se comparte la información comercial sin duplicarla por talla o color.
- SKU, precio, peso, carrito e inventario se gestionan en el nivel correcto: la variante.
- El catálogo admite distintos números de opciones y valores por producto.
- Las posiciones permiten presentar opciones y valores en un orden controlado.
- Las imágenes generales y las específicas de variante pueden coexistir.

### Negativas

- Consultar una ficha completa exige varios joins y una carga cuidadosa de relaciones.
- `uq_variante_opcion_valor` solo evita repetir una pareja; permite mezclar productos y varios valores de una misma opción si la aplicación no lo impide.
- No existe una restricción de tienda porque el modelo actual no define tiendas; un uso multi-tienda requeriría ampliar las reglas de pertenencia.
- Crear o actualizar variantes es una operación de varios pasos que debe ser transaccional.
- Los SKU pueden ser nulos y la unicidad de PostgreSQL no convierte los valores nulos en una identificación obligatoria; la aplicación debe decidir cuándo exigirlos.
