# Datos de prueba

## Objetivo

Definir un conjunto mínimo, reproducible y claramente ficticio de datos para demostrar el recorrido principal del sistema: catálogo con variantes, inventario, carrito, checkout, pedido, pagos, reembolso, envío y seguimiento. Los datos también deben permitir comprobar las relaciones del esquema y detectar errores de integridad sin depender de servicios externos.

La fuente de verdad estructural es `database/01_esquema_shopify.sql` más `database/migrations/V2–V19.sql`: 59 tablas y 86 claves foráneas. El archivo fuente incluye solamente definiciones, índices y comentarios; **no contiene sentencias `INSERT` ni un seed ejecutable**. Actualmente tampoco se proporciona otro archivo de datos de prueba. El bloque SQL de este documento es una plantilla para que cada desarrollador cree un archivo local; no afirma que ese archivo ya exista.

## Mínimos para demostrar el sistema

Como mínimo funcional se recomienda reunir los siguientes escenarios:

1. **Identidad y CUSTOMER:** un OPERATOR y un CUSTOMER con email propio, con una dirección sintética. (Estado actual pre-B: el CUSTOMER demo cuelga de un `usuario`; objetivo B: email propio sin `usuario_id`.)
2. **Catálogo comprable:** un proveedor, un producto activo, dos opciones y dos variantes con SKU, precio y combinaciones de valores distintas.
3. **Inventario:** una ubicación, un artículo de inventario por variante, niveles de stock y movimientos de entrada.
4. **Compra:** un carrito convertido con dos líneas y una sesión de checkout completada, con importes coherentes.
5. **Pedido histórico:** un pedido con dos líneas, dos direcciones, descuento e historial de estados. Las líneas deben conservar título, variante, SKU y precios aunque después cambie el catálogo.
6. **Pagos:** un intento rechazado y otro aprobado para el mismo pedido, eventos de ambos y un reembolso parcial asociado al pago aprobado.
7. **Envío:** un envío entregado con las dos líneas del pedido y eventos de seguimiento ordenados cronológicamente.
8. **Estados alternativos:** para una demostración más completa, agregar un segundo pedido pendiente y un carrito abandonado. Esto evita presentar únicamente el camino exitoso.

El ejemplo SQL incluido más adelante implementa el núcleo de los escenarios 1 a 7. No pretende poblar las 58 tablas ni sustituye los datos adicionales sugeridos para seguridad, colecciones, reseñas y listas de deseos.

## Cantidades sugeridas

Las cantidades son una guía para una demostración pequeña, no una obligación del esquema. El ejemplo reducido puede contener menos filas que esta propuesta ampliada.

| Tabla o grupo | Cantidad sugerida | Propósito |
|---|---:|---|
| `usuarios` | 3 | OPERATOR, ADMIN y el titular del CUSTOMER (actual pre-B). |
| `tiendas` | 2 | Tienda Demo y Tienda Demo Dos (V10). |
| `tienda_usuarios` | 1 | Admin de la tienda dos (V12); el resto de roles van en el backend. |
| `usuario_codigos_recuperacion` | 0 | Sin seed: se generan al enrolar TOTP. |
| `roles`, `permisos` | 3 y 5 | Distinguir administración, operación y compra. |
| `rol_permisos`, `usuario_roles` | 8 y 3 | Demostrar autorización muchos a muchos sin duplicar asignaciones. |
| `clientes`, `direcciones` | 2 y 3 | Un CUSTOMER vinculado a usuario (actual pre-B; objetivo B: email propio) y otro invitado; direcciones de envío y facturación. |
| `sesiones` | 2 | Una vigente y una expirada, ambas con tokens exclusivamente de demostración. |
| `registros_auditoria` | 4 | Altas y cambios de estado con JSON anterior y nuevo. |
| `proveedores` | 2 | Comprobar agrupación y borrado lógico por `activo`. |
| `productos` | 4 | Dos activos, un borrador y un archivado. |
| `producto_opciones` | 5 | Talla y color en productos configurables; una opción en otro producto. |
| `producto_opcion_valores` | 10 | Al menos dos valores por opción principal. |
| `producto_variantes` | 8 | Variantes con SKU, precios y pesos diferentes. |
| `variante_opcion_valores` | 16 | Dos valores por variante configurable, siempre del mismo producto. |
| `producto_imagenes` | 6 | Imágenes generales y asociadas a variante, usando `example.com`. |
| `colecciones`, `coleccion_productos` | 2 y 5 | Colección destacada y colección de temporada ficticias. |
| `etiquetas`, `producto_etiquetas` | 4 y 6 | Clasificación y filtrado del catálogo. |
| `metacampos` | 6 | Atributos complementarios sobre entidades existentes. |
| `ubicaciones` | 2 | Almacén principal y punto secundario ficticios. |
| `articulos_inventario` | 8 | Uno por variante en el dataset propuesto. |
| `niveles_inventario` | 12 | Stock de algunas variantes en dos ubicaciones y otras solo en una. |
| `movimientos_inventario` | 12 | Entradas, reservas, liberaciones y ajustes con cantidades no nulas. |
| `carritos`, `carrito_items` | 3 y 5 | Carritos convertido, activo y abandonado. |
| `metodos_envio` | 2 | Estándar y recojo ficticio, este último con precio cero. |
| `tasas_impuesto` | 1 | Una tasa de demostración; el cálculo lo realiza la aplicación. |
| `checkout_sesiones` | 2 | Una completada y otra pendiente. |
| `pedidos` | 2 | Uno entregado y uno pendiente. |
| `pedido_impuestos` | 1 | Snapshot del impuesto del pedido dos (V9). |
| `pedido_items` | 4 | Dos líneas por pedido, con snapshots históricos. |
| `pedido_direcciones` | 4 | Una de envío y una de facturación por pedido. |
| `historial_estados_pedido` | 6 | Transiciones iniciales, pago, preparación, envío y entrega. |
| `pasarelas_pago` | 4 | Registros de configuración para Stripe, Culqi, Yape y PayPal, sin credenciales reales. |
| `pagos` | 3 | Un intento rechazado, uno aprobado y uno pendiente. |
| `pago_eventos` | 6 | Creación y resolución de los intentos de pago. |
| `reembolsos` | 1 | Reembolso parcial del pago aprobado. |
| `transportistas` | 2 | Dos transportistas ficticios con URL bajo `example.com`. |
| `envios` | 2 | Uno entregado y otro en preparación. |
| `envio_items` | 3 | Contenido distribuido entre los envíos. |
| `envio_eventos` | 6 | Preparación, salida, tránsito y entrega con fechas coherentes. |
| `descuentos`, `pedido_descuentos` | 2 y 2 | Porcentaje aplicado y monto fijo sin uso. |
| `descuento_productos`, `descuento_colecciones` | 2 y 1 | Restricciones de alcance de descuentos. |
| `resenas` | 4 | Calificaciones de 1 a 5, aprobadas y pendientes. |
| `listas_deseos`, `lista_deseo_items` | 2 y 3 | Favoritos de ambos CUSTOMERs. |

## Orden de inserción y dependencias

El siguiente orden respeta las 86 claves foráneas. Las tablas de una misma etapa pueden cargarse en cualquier orden si no se referencian entre sí. Aunque `metacampos` y la pareja `entidad`/`entidad_id` de `registros_auditoria` no tienen FK hacia las entidades citadas, se dejan al final para mantener también su coherencia lógica.

| Etapa | Tablas | Dependencias ya resueltas |
|---:|---|---|
| 1 | `tiendas`, `usuarios`, `roles`, `permisos`, `proveedores`, `colecciones`, `etiquetas`, `pasarelas_pago`, `transportistas` | No tienen FK saliente. |
| 2 | `rol_permisos`, `usuario_roles`, `tienda_usuarios`, `usuario_codigos_recuperacion`, `clientes`, `sesiones`, `productos`, `ubicaciones`, `metodos_envio`, `tasas_impuesto`, `descuentos`, `tienda_pasarelas` | Usuarios, tiendas, roles, permisos, proveedores y pasarelas. |
| 3 | `direcciones`, `producto_opciones`, `producto_variantes`, `coleccion_productos`, `producto_etiquetas`, `descuento_productos`, `descuento_colecciones`, `listas_deseos`, `resenas` | Clientes, productos, colecciones, etiquetas y descuentos. |
| 4 | `producto_opcion_valores`, `articulos_inventario`, `carritos` | Opciones de producto, variantes, tiendas y clientes. |
| 5 | `variante_opcion_valores`, `producto_imagenes`, `niveles_inventario`, `lista_deseo_items` | Valores de opción, variantes, artículos, ubicaciones, listas y productos. |
| 6 | `movimientos_inventario`, `carrito_items` | Niveles de inventario, usuarios, carritos y variantes. |
| 7 | `checkout_sesiones` | Carritos, clientes, tiendas, direcciones y métodos de envío. |
| 8 | `pedidos` | Tiendas, clientes y sesiones de checkout. |
| 9 | `pedido_items`, `pedido_direcciones`, `pedido_impuestos`, `historial_estados_pedido`, `pagos`, `envios`, `pedido_descuentos` | Pedidos, variantes, usuarios, pasarelas, transportistas, ubicaciones y descuentos. |
| 10 | `pago_eventos`, `reembolsos`, `envio_items`, `envio_eventos` | Pagos, pedidos, envíos y líneas de pedido. |
| 11 | `metacampos`, `registros_auditoria` | Usuarios cuando aplique y entidades destino por coherencia lógica, no por FK. |

`tasas_impuesto` es un catálogo independiente en el SQL actual: cargarlo no calcula ni relaciona automáticamente impuestos. Los importes de `checkout_sesiones` y `pedidos` deben ser calculados y escritos por el proceso que genera los datos o por la aplicación.

## Convenciones para datos ficticios

- Usar nombres como `Customer Demo`, `Operador Demo` y `Proveedor Ficticio`; no copiar nombres, teléfonos, documentos ni direcciones de personas reales.
- Usar correos bajo `example.com`, por ejemplo `customer.demo@example.com`. Ese dominio está reservado para ejemplos.
- Usar URL e imágenes bajo `https://example.com/` y transportistas o códigos de tracking inexistentes con prefijo `DEMO`.
- Usar direcciones imposibles o neutras, como `Calle Ficticia 123`, `Ciudad Demo`, `País Demo`, y teléfonos `000000000` o `NULL`.
- Para IP, utilizar rangos de documentación como `192.0.2.0/24` y `198.51.100.0/24`.
- Los valores de `password_hash`, tokens de sesión, carritos, pagos y reembolsos del ejemplo son marcadores no válidos para autenticación o integración real. No deben emplearse como secretos.
- Mantener una sola moneda por operación (`PEN` en el ejemplo) y comprobar los importes antes de cargar.
- El dataset reducido se ejecuta una sola vez sobre una base vacía. Sus valores únicos provocarán conflictos si se ejecuta de nuevo sin recrear esa base desechable.

## Coherencia del ejemplo económico

El pedido de demostración contiene:

| Línea | Cantidad | Precio unitario | Precio total |
|---|---:|---:|---:|
| Camiseta Demo, talla M, color Azul | 2 | 50.00 | 100.00 |
| Camiseta Demo, talla L, color Verde | 1 | 60.00 | 60.00 |
| **Subtotal** | | | **160.00** |

Se aplica un descuento del 10 % (`16.00`). Para hacer explícito el supuesto de cálculo, el impuesto de demostración del 18 % se calcula sobre el subtotal después del descuento: `(160.00 - 16.00) * 0.18 = 25.92`. Con envío de `10.00`, el total es:

```text
160.00 - 16.00 + 25.92 + 10.00 = 179.92
```

El checkout, el pedido y el pago aprobado usan esos mismos importes. El reembolso aprobado de `20.00` es parcial, pertenece al pago aprobado y no cambia el pedido entregado al estado `reembolsado`.

## Cargar el esquema con `psql`

En PowerShell, desde la raíz del proyecto, se puede crear una base desechable y ejecutar directamente el esquema:

```powershell
createdb -U postgres shopify_db_demo
psql -U postgres -d shopify_db_demo -v ON_ERROR_STOP=1 -f database/01_esquema_shopify.sql
Get-ChildItem database/migrations/V*.sql | Sort-Object Name | ForEach-Object {
  psql -U postgres -d shopify_db_demo -v ON_ERROR_STOP=1 -f $_.FullName
}
```

En el backend, Flyway aplica ese mismo orden al arrancar (V1 del classpath + `filesystem:../shopify_db/database/migrations`). La migración repetible `R__roles_base.sql` inserta los roles del RBAC (`super_admin`, `admin`, `operador`, `cliente`) — actual pre-B; objetivo B: solo `super_admin` global; es dato de referencia, no seed de demostración.

El comentario final de la base de datos utiliza `current_database()`, por lo que se aplica correctamente tanto a `shopify_db_demo` como a cualquier otro nombre de base. El comando presupone que PostgreSQL está instalado, que `createdb` y `psql` están en `PATH` y que el usuario tiene permiso para crear la base. La contraseña debe proporcionarse mediante el mecanismo seguro configurado en PostgreSQL, no escribirse en el script.

Comprobación estructural posterior:

```sql
SELECT COUNT(*) AS tablas_publicas
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

SELECT COUNT(*) AS claves_foraneas
FROM information_schema.table_constraints
WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY';
```

Los resultados esperados para este esquema, sin objetos adicionales en `public`, son `59` y `86`.

## Crear y ejecutar un seed local

1. Copiar el bloque siguiente en un archivo local creado por el desarrollador, por ejemplo `seed_demo.local.sql`. **Ese archivo no forma parte de la entrega y no existe hasta que alguien lo cree.** El bloque es compatible con las migraciones V2–V19: las columnas nuevas usan sus DEFAULT y los inserts respetan los UNIQUE/NOT NULL añadidos.
2. Guardarlo en UTF-8. El bloque usa `\gset`, una metainstrucción de `psql`, para conservar los identificadores generados sin fijar manualmente los valores de las secuencias; debe ejecutarse con `psql`, no pegarse sin cambios en un cliente SQL que no reconozca esa instrucción.
3. Ejecutarlo una vez sobre la base vacía:

```powershell
psql -U postgres -d shopify_db_demo -v ON_ERROR_STOP=1 -f seed_demo.local.sql
```

4. La transacción termina con `COMMIT`. Si cualquier sentencia falla, `ON_ERROR_STOP=1` detiene `psql` y el cierre de conexión revierte la transacción incompleta. Para ensayar sin conservar datos, se puede sustituir temporalmente el `COMMIT` final por `ROLLBACK`.

```sql
-- Seed demo con esquema ACTUAL pre-B (clientes.usuario_id obligatorio).
-- Objetivo B (V20): clientes.email propio; el bloque se actualizará en F1.
\set ON_ERROR_STOP on

BEGIN;

-- Usuarios y CUSTOMER: las credenciales son marcadores ficticios, no hashes utilizables.
INSERT INTO usuarios (email, password_hash, nombre, apellido)
VALUES ('operador.demo@example.com', 'demo-no-es-un-hash-operador', 'Operador', 'Demo')
RETURNING id AS id_operador \gset

INSERT INTO usuarios (email, password_hash, nombre, apellido)
VALUES ('customer.demo@example.com', 'demo-no-es-un-hash-customer', 'Customer', 'Demo')
RETURNING id AS id_usuario_customer \gset

-- Tenant demo (V11): todo lo pre multi-tenant cae aquí.
INSERT INTO tiendas (nombre, slug, descripcion)
VALUES ('Tienda Demo', 'tienda-demo', 'Tenant inicial ficticio')
RETURNING id AS id_tienda \gset

INSERT INTO clientes (usuario_id, tienda_id, nombre, apellido, telefono, acepta_marketing)
VALUES (:id_usuario_customer, :id_tienda, 'Customer', 'Demo', '000000000', FALSE)
RETURNING id AS id_cliente \gset

INSERT INTO direcciones (
  cliente_id, linea1, linea2, ciudad, departamento,
  codigo_postal, pais, es_principal
)
VALUES (
  :id_cliente, 'Calle Ficticia 123', 'Interior Demo', 'Ciudad Demo',
  'Region Demo', '00000', 'Pais Demo', TRUE
)
RETURNING id AS id_direccion \gset

-- Catálogo: una camiseta con dos variantes y opciones del mismo producto.
INSERT INTO proveedores (nombre, slug, descripcion)
VALUES ('Proveedor Ficticio Demo', 'proveedor-ficticio-demo', 'Registro exclusivamente de prueba')
RETURNING id AS id_proveedor \gset

INSERT INTO productos (
  tienda_id, proveedor_id, titulo, slug, descripcion, estado, publicado_en
)
VALUES (
  :id_tienda, :id_proveedor, 'Camiseta de Algodon Demo', 'camiseta-algodon-demo',
  'Producto sintetico para pruebas', 'activo', '2026-10-01 09:00:00+00'
)
RETURNING id AS id_producto \gset

INSERT INTO producto_opciones (producto_id, nombre, posicion)
VALUES (:id_producto, 'Talla', 1)
RETURNING id AS id_opcion_talla \gset

INSERT INTO producto_opciones (producto_id, nombre, posicion)
VALUES (:id_producto, 'Color', 2)
RETURNING id AS id_opcion_color \gset

INSERT INTO producto_opcion_valores (producto_opcion_id, valor, posicion)
VALUES (:id_opcion_talla, 'M', 1)
RETURNING id AS id_valor_m \gset

INSERT INTO producto_opcion_valores (producto_opcion_id, valor, posicion)
VALUES (:id_opcion_talla, 'L', 2)
RETURNING id AS id_valor_l \gset

INSERT INTO producto_opcion_valores (producto_opcion_id, valor, posicion)
VALUES (:id_opcion_color, 'Azul', 1)
RETURNING id AS id_valor_azul \gset

INSERT INTO producto_opcion_valores (producto_opcion_id, valor, posicion)
VALUES (:id_opcion_color, 'Verde', 2)
RETURNING id AS id_valor_verde \gset

INSERT INTO producto_variantes (
  producto_id, sku, precio, precio_comparativo, peso_gramos
)
VALUES (:id_producto, 'DEMO-CAMI-M-AZUL', 50.00, 60.00, 180)
RETURNING id AS id_variante_m \gset

INSERT INTO producto_variantes (
  producto_id, sku, precio, precio_comparativo, peso_gramos
)
VALUES (:id_producto, 'DEMO-CAMI-L-VERDE', 60.00, 70.00, 190)
RETURNING id AS id_variante_l \gset

INSERT INTO variante_opcion_valores (variante_id, opcion_valor_id)
VALUES
  (:id_variante_m, :id_valor_m),
  (:id_variante_m, :id_valor_azul),
  (:id_variante_l, :id_valor_l),
  (:id_variante_l, :id_valor_verde);

-- Inventario: 20 y 15 unidades iniciales, con reservas y salidas posteriores.
INSERT INTO ubicaciones (tienda_id, nombre, direccion, ciudad, pais)
VALUES (:id_tienda, 'Almacen Ficticio Demo', 'Calle Ficticia 123', 'Ciudad Demo', 'Pais Demo')
RETURNING id AS id_ubicacion \gset

INSERT INTO articulos_inventario (variante_id, sku, requiere_envio, controla_stock)
VALUES (:id_variante_m, 'DEMO-CAMI-M-AZUL', TRUE, TRUE)
RETURNING id AS id_articulo_m \gset

INSERT INTO articulos_inventario (variante_id, sku, requiere_envio, controla_stock)
VALUES (:id_variante_l, 'DEMO-CAMI-L-VERDE', TRUE, TRUE)
RETURNING id AS id_articulo_l \gset

INSERT INTO niveles_inventario (
  articulo_inventario_id, ubicacion_id, disponible, reservado
)
VALUES (:id_articulo_m, :id_ubicacion, 18, 0)
RETURNING id AS id_nivel_m \gset

INSERT INTO niveles_inventario (
  articulo_inventario_id, ubicacion_id, disponible, reservado
)
VALUES (:id_articulo_l, :id_ubicacion, 14, 0)
RETURNING id AS id_nivel_l \gset

INSERT INTO movimientos_inventario (
  nivel_inventario_id, tipo_movimiento, cantidad, motivo, referencia,
  usuario_id, creado_en
)
VALUES
  (:id_nivel_m, 'entrada', 20, 'Stock inicial ficticio', 'DEMO-ENTRADA-M',
   :id_operador, '2026-10-01 09:00:00+00'),
  (:id_nivel_l, 'entrada', 15, 'Stock inicial ficticio', 'DEMO-ENTRADA-L',
   :id_operador, '2026-10-01 09:00:00+00'),
  (:id_nivel_m, 'reserva', 2, 'Reserva ficticia del pedido', 'DEMO-0001',
   :id_operador, '2026-10-20 10:00:00+00'),
  (:id_nivel_l, 'reserva', 1, 'Reserva ficticia del pedido', 'DEMO-0001',
   :id_operador, '2026-10-20 10:00:00+00'),
  (:id_nivel_m, 'salida', 2, 'Despacho ficticio del pedido', 'DEMO-0001',
   :id_operador, '2026-10-21 09:00:00+00'),
  (:id_nivel_l, 'salida', 1, 'Despacho ficticio del pedido', 'DEMO-0001',
   :id_operador, '2026-10-21 09:00:00+00');

-- Carrito y checkout convertidos en el pedido.
INSERT INTO carritos (
  tienda_id, cliente_id, token, moneda, estado, creado_en, actualizado_en, expira_en
)
VALUES (
  :id_tienda, :id_cliente, 'carrito-demo-no-valido-0001', 'PEN', 'convertido',
  '2026-10-20 09:50:00+00', '2026-10-20 10:00:00+00',
  '2026-10-20 10:30:00+00'
)
RETURNING id AS id_carrito \gset

INSERT INTO carrito_items (carrito_id, variante_id, cantidad, precio_unitario)
VALUES
  (:id_carrito, :id_variante_m, 2, 50.00),
  (:id_carrito, :id_variante_l, 1, 60.00);

INSERT INTO metodos_envio (tienda_id, nombre, descripcion, precio)
VALUES (:id_tienda, 'Envio ficticio estandar', 'No representa un servicio real', 10.00)
RETURNING id AS id_metodo_envio \gset

INSERT INTO tasas_impuesto (tienda_id, nombre, pais, departamento, porcentaje)
VALUES (:id_tienda, 'Tasa ficticia demo', 'Pais Demo', 'Region Demo', 18.00)
RETURNING id AS id_tasa_demo \gset

INSERT INTO checkout_sesiones (
  tienda_id, carrito_id, cliente_id, direccion_envio_id, direccion_facturacion_id,
  metodo_envio_id, moneda, subtotal, impuesto_total, envio_total,
  descuento_total, total, estado, expira_en, creado_en
)
VALUES (
  :id_tienda, :id_carrito, :id_cliente, :id_direccion, :id_direccion,
  :id_metodo_envio, 'PEN', 160.00, 25.92, 10.00,
  16.00, 179.92, 'completado', '2026-10-20 10:30:00+00',
  '2026-10-20 09:55:00+00'
)
RETURNING id AS id_checkout \gset

INSERT INTO descuentos (
  tienda_id, codigo, tipo, valor, descripcion, uso_maximo, usos_actuales,
  inicia_en, termina_en, activo
)
VALUES (
  :id_tienda, 'DEMO10', 'porcentaje', 10.00, 'Descuento sintetico del diez por ciento',
  100, 1, '2026-01-01 00:00:00+00', '2026-12-31 23:59:59+00', TRUE
)
RETURNING id AS id_descuento \gset

-- Pedido y snapshots de las dos lineas.
INSERT INTO pedidos (
  tienda_id, cliente_id, checkout_sesion_id, numero_pedido, estado, moneda,
  subtotal, impuesto_total, envio_total, descuento_total, total,
  realizado_en, creado_en, actualizado_en
)
VALUES (
  :id_tienda, :id_cliente, :id_checkout, 'DEMO-0001', 'entregado', 'PEN',
  160.00, 25.92, 10.00, 16.00, 179.92,
  '2026-10-20 10:00:00+00', '2026-10-20 10:00:00+00',
  '2026-10-24 09:00:00+00'
)
RETURNING id AS id_pedido \gset

INSERT INTO pedido_items (
  pedido_id, variante_id, producto_titulo, variante_titulo,
  sku, cantidad, precio_unitario, precio_total
)
VALUES
  (
    :id_pedido, :id_variante_m, 'Camiseta de Algodon Demo',
    'Talla M / Color Azul', 'DEMO-CAMI-M-AZUL', 2, 50.00, 100.00
  ),
  (
    :id_pedido, :id_variante_l, 'Camiseta de Algodon Demo',
    'Talla L / Color Verde', 'DEMO-CAMI-L-VERDE', 1, 60.00, 60.00
  );

INSERT INTO pedido_direcciones (
  pedido_id, tipo, nombre_destinatario, linea1, linea2,
  ciudad, departamento, codigo_postal, pais, telefono
)
VALUES
  (
    :id_pedido, 'envio', 'Cliente Demo', 'Calle Ficticia 123', 'Interior Demo',
    'Ciudad Demo', 'Region Demo', '00000', 'Pais Demo', '000000000'
  ),
  (
    :id_pedido, 'facturacion', 'Cliente Demo', 'Calle Ficticia 123', 'Interior Demo',
    'Ciudad Demo', 'Region Demo', '00000', 'Pais Demo', '000000000'
  );

INSERT INTO historial_estados_pedido (
  pedido_id, estado_anterior, estado_nuevo, comentario, usuario_id, creado_en
)
VALUES
  (:id_pedido, NULL, 'pendiente', 'Pedido ficticio creado', :id_operador, '2026-10-20 10:00:00+00'),
  (:id_pedido, 'pendiente', 'pagado', 'Pago ficticio aprobado', :id_operador, '2026-10-20 10:11:00+00'),
  (:id_pedido, 'pagado', 'en_preparacion', 'Preparacion ficticia', :id_operador, '2026-10-20 12:00:00+00'),
  (:id_pedido, 'en_preparacion', 'enviado', 'Salida ficticia del almacen', :id_operador, '2026-10-21 09:00:00+00'),
  (:id_pedido, 'enviado', 'entregado', 'Entrega ficticia', :id_operador, '2026-10-23 14:00:00+00');

INSERT INTO pedido_descuentos (pedido_id, descuento_id, monto_aplicado)
VALUES (:id_pedido, :id_descuento, 16.00);

-- Dos intentos para el mismo pedido: uno rechazado y otro aprobado.
INSERT INTO pasarelas_pago (nombre, codigo, descripcion)
VALUES ('Pasarela Ficticia Demo', 'DEMO', 'No conecta con ningun proveedor real')
RETURNING id AS id_pasarela \gset

INSERT INTO pagos (
  pedido_id, pasarela_pago_id, estado, monto, moneda,
  transaccion_externa_id, metodo, pagado_en, creado_en
)
VALUES (
  :id_pedido, :id_pasarela, 'rechazado', 179.92, 'PEN',
  'demo-transaccion-rechazada-0001', 'tarjeta_demo', NULL,
  '2026-10-20 10:02:00+00'
)
RETURNING id AS id_pago_rechazado \gset

INSERT INTO pago_eventos (
  pago_id, tipo_evento, estado_anterior, estado_nuevo,
  payload, referencia_externa, creado_en
)
VALUES (
  :id_pago_rechazado, 'pago.rechazado', 'pendiente', 'rechazado',
  '{"origen":"demo","motivo":"rechazo_simulado"}'::jsonb,
  'demo-evento-rechazado-0001', '2026-10-20 10:03:00+00'
);

INSERT INTO pagos (
  pedido_id, pasarela_pago_id, estado, monto, moneda,
  transaccion_externa_id, metodo, pagado_en, creado_en
)
VALUES (
  :id_pedido, :id_pasarela, 'aprobado', 179.92, 'PEN',
  'demo-transaccion-aprobada-0001', 'tarjeta_demo',
  '2026-10-20 10:11:00+00', '2026-10-20 10:10:00+00'
)
RETURNING id AS id_pago_aprobado \gset

INSERT INTO pago_eventos (
  pago_id, tipo_evento, estado_anterior, estado_nuevo,
  payload, referencia_externa, creado_en
)
VALUES
  (
    :id_pago_aprobado, 'pago.creado', NULL, 'pendiente',
    '{"origen":"demo"}'::jsonb, 'demo-evento-creado-0001',
    '2026-10-20 10:10:00+00'
  ),
  (
    :id_pago_aprobado, 'pago.aprobado', 'pendiente', 'aprobado',
    '{"origen":"demo","resultado":"aprobado"}'::jsonb,
    'demo-evento-aprobado-0001', '2026-10-20 10:11:00+00'
  );

INSERT INTO reembolsos (
  pedido_id, pago_id, monto, moneda, motivo, estado,
  transaccion_externa_id, creado_en
)
VALUES (
  :id_pedido, :id_pago_aprobado, 20.00, 'PEN',
  'Reembolso parcial ficticio', 'aprobado',
  'demo-reembolso-0001', '2026-10-24 09:00:00+00'
);

-- Envio completo y seguimiento; todas las referencias son inexistentes.
INSERT INTO transportistas (nombre, codigo, url_tracking)
VALUES (
  'Transporte Ficticio Demo', 'DEMO',
  'https://example.com/tracking-demo'
)
RETURNING id AS id_transportista \gset

INSERT INTO envios (
  pedido_id, transportista_id, ubicacion_origen_id, numero_tracking,
  url_tracking, transportista, estado, enviado_en, entregado_en, creado_en
)
VALUES (
  :id_pedido, :id_transportista, :id_ubicacion, 'DEMO-TRACK-0001',
  'https://example.com/tracking-demo/DEMO-TRACK-0001',
  'Transporte Ficticio Demo', 'entregado',
  '2026-10-21 09:00:00+00', '2026-10-23 14:00:00+00',
  '2026-10-20 12:00:00+00'
)
RETURNING id AS id_envio \gset

INSERT INTO envio_items (envio_id, pedido_item_id, cantidad)
SELECT :id_envio, id, cantidad
FROM pedido_items
WHERE pedido_id = :id_pedido;

INSERT INTO envio_eventos (
  envio_id, estado, descripcion, ubicacion,
  ocurrido_en, referencia_externa, creado_en
)
VALUES
  (
    :id_envio, 'preparando', 'Paquete ficticio preparado', 'Almacen Demo',
    '2026-10-20 12:00:00+00', 'demo-evento-envio-01',
    '2026-10-20 12:00:00+00'
  ),
  (
    :id_envio, 'enviado', 'Salida ficticia', 'Ciudad Demo',
    '2026-10-21 09:00:00+00', 'demo-evento-envio-02',
    '2026-10-21 09:00:00+00'
  ),
  (
    :id_envio, 'en_transito', 'Transito ficticio', 'Punto Intermedio Demo',
    '2026-10-22 08:00:00+00', 'demo-evento-envio-03',
    '2026-10-22 08:00:00+00'
  ),
  (
    :id_envio, 'entregado', 'Entrega ficticia', 'Ciudad Demo',
    '2026-10-23 14:00:00+00', 'demo-evento-envio-04',
    '2026-10-23 14:00:00+00'
  );

-- Referencias logicas validas hacia el producto recien creado.
INSERT INTO metacampos (entidad, entidad_id, clave, valor, tipo)
VALUES (
  'producto', :id_producto, 'origen_demo',
  'Dato sintetico sin valor comercial', 'texto'
);

INSERT INTO registros_auditoria (
  usuario_id, entidad, entidad_id, accion,
  datos_anteriores, datos_nuevos, ip, creado_en
)
VALUES (
  :id_operador, 'producto', :id_producto, 'creacion_demo',
  NULL, '{"titulo":"Camiseta de Algodon Demo","ficticio":true}'::jsonb,
  '192.0.2.10', '2026-10-01 09:00:00+00'
);

-- Segundo tenant: tienda aislada con su compra mínima (V10–V12).
INSERT INTO tiendas (nombre, slug, descripcion)
VALUES ('Tienda Demo Dos', 'tienda-demo-dos', 'Segundo tenant ficticio')
RETURNING id AS id_tienda2 \gset

INSERT INTO usuarios (email, password_hash, nombre, apellido)
VALUES ('admin2.demo@example.com', 'demo-no-es-hash-admin2', 'Admin', 'Dos')
RETURNING id AS id_admin2 \gset

INSERT INTO usuarios (email, password_hash, nombre, apellido)
VALUES ('customer2.demo@example.com', 'demo-no-es-hash-customer2', 'Customer', 'Dos')
RETURNING id AS id_usuario_customer2 \gset

INSERT INTO tienda_usuarios (tienda_id, usuario_id, rol_tienda)
VALUES (:id_tienda2, :id_admin2, 'admin');

INSERT INTO clientes (usuario_id, tienda_id, nombre, apellido, telefono, acepta_marketing)
VALUES (:id_usuario_customer2, :id_tienda2, 'Customer', 'Dos', '000000001', FALSE)
RETURNING id AS id_cliente2 \gset

INSERT INTO ubicaciones (tienda_id, nombre, direccion, ciudad, pais)
VALUES (:id_tienda2, 'Almacen Dos Demo', 'Calle Dos 456', 'Ciudad Demo', 'Pais Demo')
RETURNING id AS id_ubicacion2 \gset

INSERT INTO productos (tienda_id, titulo, slug, descripcion, estado, publicado_en)
VALUES (
  :id_tienda2, 'Gorra Demo Dos', 'gorra-demo-dos',
  'Producto del segundo tenant', 'activo', '2026-10-21 09:00:00+00'
)
RETURNING id AS id_producto2 \gset

INSERT INTO producto_variantes (producto_id, sku, precio)
VALUES (:id_producto2, 'DEMO-GORRA-U', 30.00)
RETURNING id AS id_variante2 \gset

INSERT INTO articulos_inventario (variante_id, sku, requiere_envio, controla_stock)
VALUES (:id_variante2, 'DEMO-GORRA-U', TRUE, TRUE)
RETURNING id AS id_articulo2 \gset

INSERT INTO niveles_inventario (articulo_inventario_id, ubicacion_id, disponible, reservado)
VALUES (:id_articulo2, :id_ubicacion2, 10, 0);

INSERT INTO carritos (tienda_id, cliente_id, token, moneda, estado, expira_en)
VALUES (:id_tienda2, :id_cliente2, 'carrito-demo-dos-0001', 'PEN', 'convertido', '2026-10-21 10:30:00+00')
RETURNING id AS id_carrito2 \gset

INSERT INTO carrito_items (carrito_id, variante_id, cantidad, precio_unitario)
VALUES (:id_carrito2, :id_variante2, 1, 30.00);

INSERT INTO checkout_sesiones (
  tienda_id, carrito_id, cliente_id, moneda, subtotal, impuesto_total,
  envio_total, descuento_total, total, estado, expira_en
)
VALUES (
  :id_tienda2, :id_carrito2, :id_cliente2, 'PEN', 30.00, 5.40,
  10.00, 0, 45.40, 'completado', '2026-10-21 10:30:00+00'
)
RETURNING id AS id_checkout2 \gset

INSERT INTO pedidos (
  tienda_id, cliente_id, checkout_sesion_id, numero_pedido, estado, moneda,
  subtotal, impuesto_total, envio_total, descuento_total, total, realizado_en
)
VALUES (
  :id_tienda2, :id_cliente2, :id_checkout2, 'DEMO2-0001', 'pagado', 'PEN',
  30.00, 5.40, 10.00, 0, 45.40, '2026-10-21 10:00:00+00'
)
RETURNING id AS id_pedido2 \gset

INSERT INTO pedido_items (
  pedido_id, variante_id, producto_titulo, variante_titulo,
  sku, cantidad, precio_unitario, precio_total
)
VALUES (
  :id_pedido2, :id_variante2, 'Gorra Demo Dos',
  'Talla U', 'DEMO-GORRA-U', 1, 30.00, 30.00
)
RETURNING id AS id_item2 \gset

INSERT INTO pedido_impuestos (pedido_id, pedido_item_id, titulo, tasa, monto)
VALUES (:id_pedido2, :id_item2, 'IGV Demo', 18.00, 5.40);

INSERT INTO pedido_direcciones (pedido_id, tipo, nombre_destinatario, linea1, ciudad, pais)
VALUES (:id_pedido2, 'envio', 'Cliente Dos', 'Calle Dos 456', 'Ciudad Demo', 'Pais Demo');

INSERT INTO pagos (
  pedido_id, pasarela_pago_id, estado, monto, moneda,
  transaccion_externa_id, metodo, pagado_en
)
VALUES (
  :id_pedido2, :id_pasarela, 'aprobado', 45.40, 'PEN',
  'demo-dos-transaccion-0001', 'tarjeta_demo', '2026-10-21 10:11:00+00'
);

COMMIT;
```

## Comprobaciones posteriores al seed

Estas consultas no modifican datos y ayudan a validar el recorrido:

```sql
-- Pedidos por tienda: DEMO-0001 en tienda-demo, DEMO2-0001 en tienda-demo-dos.
SELECT t.slug, p.numero_pedido, p.estado, p.total
FROM pedidos AS p
JOIN tiendas AS t ON t.id = p.tienda_id
ORDER BY t.slug, p.numero_pedido;

-- Fuga entre tenants: debe dar 0 (líneas cuyo producto es de otra tienda que el pedido).
SELECT COUNT(*)
FROM pedido_items AS pi
JOIN pedidos AS p ON p.id = pi.pedido_id
JOIN producto_variantes AS v ON v.id = pi.variante_id
JOIN productos AS pr ON pr.id = v.producto_id
WHERE p.tienda_id <> pr.tienda_id;

```sql
-- El subtotal debe coincidir con la suma de los snapshots de las lineas.
SELECT p.numero_pedido, p.subtotal, SUM(pi.precio_total) AS suma_items
FROM pedidos AS p
JOIN pedido_items AS pi ON pi.pedido_id = p.id
WHERE p.numero_pedido = 'DEMO-0001'
GROUP BY p.id, p.numero_pedido, p.subtotal;

-- El total calculado y el almacenado deben ser 179.92.
SELECT
  numero_pedido,
  total,
  subtotal - descuento_total + impuesto_total + envio_total AS total_calculado
FROM pedidos
WHERE numero_pedido = 'DEMO-0001';

-- Deben aparecer un pago rechazado y uno aprobado para el mismo pedido.
SELECT p.numero_pedido, pg.estado, pg.monto, pg.moneda, pg.transaccion_externa_id
FROM pagos AS pg
JOIN pedidos AS p ON p.id = pg.pedido_id
WHERE p.numero_pedido = 'DEMO-0001'
ORDER BY pg.creado_en;

-- Los eventos deben aparecer en orden cronologico.
SELECT e.numero_tracking, ee.estado, ee.ocurrido_en
FROM envios AS e
JOIN envio_eventos AS ee ON ee.envio_id = e.id
WHERE e.numero_tracking = 'DEMO-TRACK-0001'
ORDER BY ee.ocurrido_en, ee.id;

-- Cada valor de variante debe pertenecer al mismo producto que la variante.
SELECT v.sku, po.nombre AS opcion, pov.valor
FROM producto_variantes AS v
JOIN variante_opcion_valores AS vov ON vov.variante_id = v.id
JOIN producto_opcion_valores AS pov ON pov.id = vov.opcion_valor_id
JOIN producto_opciones AS po ON po.id = pov.producto_opcion_id
WHERE v.producto_id IN (
  SELECT id FROM productos WHERE slug = 'camiseta-algodon-demo'
)
ORDER BY v.sku, po.posicion, pov.posicion;
```

Para ampliar el dataset, conviene agregar el segundo pedido y los estados alternativos en una transacción separada, reutilizando las entidades existentes mediante consultas por claves únicas (`email`, `slug`, `sku` o `numero_pedido`) en lugar de suponer identificadores numéricos fijos.
