# Seguridad e integridad

## Alcance

Este documento distingue los mecanismos que ya declara `database/01_esquema_shopify.sql` de los controles que debe implementar Spring Boot. Las claves, constraints e índices descritos a continuación existen en el esquema. La autenticación de solicitudes, autorización de endpoints, verificación de firmas externas, cálculos agregados, bloqueos y escritura de auditoría no ocurren automáticamente por tener esas tablas.

## Credenciales y sesiones

### Contraseñas

- `usuarios.password_hash` es obligatorio y tiene una longitud máxima de 255 caracteres. El esquema almacena el hash, pero no selecciona el algoritmo ni realiza la derivación: es responsabilidad del backend.
- `usuarios.email` es obligatorio y único; puede servir como identificador de login. `usuarios.activo` permite impedir nuevos accesos de cuentas deshabilitadas, comprobación que debe hacer la aplicación.
- No hay columnas para contraseña en texto plano, preguntas de seguridad ni tokens de recuperación. Tampoco existe una tabla de refresh tokens; no debe suponerse su presencia.

### Sesiones staff persistidas (objetivo B: CUSTOMER usa `cliente_sesiones`, V21)

La tabla `sesiones` contiene `usuario_id`, `token_hash`, `familia_id`, `revocado_en` y `expira_en` (V8; `token_legacy` en transición hasta el cutover). Sus controles reales son:

- El refresh opaco solo existe como `token_hash` (SHA-256); el valor original únicamente lo tiene el titular. La rotación usa `familia_id` y `reemplazado_por_id`; reutilizar un refresh rotado obliga a revocar la familia entera.
- La expiración es obligatoria, pero PostgreSQL no invalida ni elimina la sesión al llegar a `expira_en`; cada autenticación debe comparar esa fecha con el instante actual.
- La relación con `usuarios` es obligatoria y tiene `ON DELETE CASCADE`: eliminar el usuario elimina sus sesiones.
- `ip` admite hasta 45 caracteres, suficiente para representar IPv4 o IPv6, y `user_agent` hasta 255. Ambos son datos de contexto, no factores de autenticación suficientes por sí mismos.

Spring Boot debe generar tokens impredecibles, validar su coincidencia y vigencia, comprobar que el usuario siga activo y eliminar la sesión al cerrarla si se adopta revocación por borrado. IP y user agent pueden ayudar a detectar anomalías, pero una sesión no debe autorizarse únicamente por esos valores.

## RBAC real del esquema

El modelo de control de acceso basado en roles está formado por tablas existentes:

| Tabla | Función | Integridad declarada |
|---|---|---|
| `roles` | Define perfiles de acceso. | `nombre` es único. |
| `permisos` | Define capacidades autorizables. | `nombre` es único. |
| `usuario_roles` | Asigna roles a usuarios. | `uq_usuario_rol` evita repetir el mismo par; ambas claves foráneas usan `CASCADE`. |
| `rol_permisos` | Asigna permisos a roles. | `uq_rol_permiso` evita repetir el mismo par; ambas claves foráneas usan `CASCADE`. |

La resolución de autoridad parte de `usuarios`, continúa por `usuario_roles` y `roles`, y llega a `permisos` mediante `rol_permisos`. Un usuario puede tener varios roles y un rol puede reunir varios permisos. El esquema no precarga nombres de roles o permisos, por lo que sus valores y la política de concesión pertenecen a la configuración de la aplicación.

Estas tablas almacenan RBAC, pero no protegen por sí mismas controladores, vistas o filas. Spring Security debe exigir autenticación, cargar las asignaciones y aplicar denegación por defecto. La titularidad de carritos, direcciones, pedidos y demás recursos es una validación adicional: tener un permiso general no sustituye comprobar que el recurso pertenece al actor.

## Integridad referencial

### Acciones de borrado declaradas

Todas las claves foráneas del esquema se enumeran a continuación. `CASCADE` propaga el borrado, `SET NULL` conserva la fila y anula la referencia, y `RESTRICT` impide borrar el padre referenciado. No se atribuyen otras acciones.

| Módulo | Clave foránea | Referencia | `ON DELETE` |
|---|---|---|---|
| Acceso | `rol_permisos.rol_id` | `roles.id` | `CASCADE` |
| Acceso | `rol_permisos.permiso_id` | `permisos.id` | `CASCADE` |
| Acceso | `usuario_roles.usuario_id` | `usuarios.id` | `CASCADE` |
| Acceso | `usuario_roles.rol_id` | `roles.id` | `CASCADE` |
| Clientes | `clientes.usuario_id` | `usuarios.id` | `RESTRICT` |
| Clientes | `direcciones.cliente_id` | `clientes.id` | `CASCADE` |
| Acceso | `sesiones.usuario_id` | `usuarios.id` | `CASCADE` |
| Auditoría | `registros_auditoria.usuario_id` | `usuarios.id` | `SET NULL` |
| Catálogo | `productos.proveedor_id` | `proveedores.id` | `SET NULL` |
| Catálogo | `producto_opciones.producto_id` | `productos.id` | `CASCADE` |
| Catálogo | `producto_opcion_valores.producto_opcion_id` | `producto_opciones.id` | `CASCADE` |
| Catálogo | `producto_variantes.producto_id` | `productos.id` | `CASCADE` |
| Catálogo | `variante_opcion_valores.variante_id` | `producto_variantes.id` | `CASCADE` |
| Catálogo | `variante_opcion_valores.opcion_valor_id` | `producto_opcion_valores.id` | `CASCADE` |
| Catálogo | `producto_imagenes.producto_id` | `productos.id` | `CASCADE` |
| Catálogo | `producto_imagenes.variante_id` | `producto_variantes.id` | `SET NULL` |
| Catálogo | `coleccion_productos.coleccion_id` | `colecciones.id` | `CASCADE` |
| Catálogo | `coleccion_productos.producto_id` | `productos.id` | `CASCADE` |
| Catálogo | `producto_etiquetas.producto_id` | `productos.id` | `CASCADE` |
| Catálogo | `producto_etiquetas.etiqueta_id` | `etiquetas.id` | `CASCADE` |
| Inventario | `articulos_inventario.variante_id` | `producto_variantes.id` | `CASCADE` |
| Inventario | `niveles_inventario.articulo_inventario_id` | `articulos_inventario.id` | `CASCADE` |
| Inventario | `niveles_inventario.ubicacion_id` | `ubicaciones.id` | `RESTRICT` |
| Inventario | `movimientos_inventario.nivel_inventario_id` | `niveles_inventario.id` | `CASCADE` |
| Inventario | `movimientos_inventario.usuario_id` | `usuarios.id` | `SET NULL` |
| Carrito | `carritos.cliente_id` | `clientes.id` | `SET NULL` |
| Carrito | `carrito_items.carrito_id` | `carritos.id` | `CASCADE` |
| Carrito | `carrito_items.variante_id` | `producto_variantes.id` | `CASCADE` |
| Checkout | `checkout_sesiones.carrito_id` | `carritos.id` | `CASCADE` |
| Checkout | `checkout_sesiones.cliente_id` | `clientes.id` | `RESTRICT` |
| Checkout | `checkout_sesiones.direccion_envio_id` | `direcciones.id` | `SET NULL` |
| Checkout | `checkout_sesiones.direccion_facturacion_id` | `direcciones.id` | `SET NULL` |
| Checkout | `checkout_sesiones.metodo_envio_id` | `metodos_envio.id` | `SET NULL` |
| Pedidos | `pedidos.cliente_id` | `clientes.id` | `RESTRICT` |
| Pedidos | `pedidos.checkout_sesion_id` | `checkout_sesiones.id` | `SET NULL` |
| Pedidos | `pedido_items.pedido_id` | `pedidos.id` | `CASCADE` |
| Pedidos | `pedido_items.variante_id` | `producto_variantes.id` | `SET NULL` |
| Pedidos | `pedido_direcciones.pedido_id` | `pedidos.id` | `CASCADE` |
| Pedidos | `historial_estados_pedido.pedido_id` | `pedidos.id` | `CASCADE` |
| Pedidos | `historial_estados_pedido.usuario_id` | `usuarios.id` | `SET NULL` |
| Pagos | `pagos.pedido_id` | `pedidos.id` | `CASCADE` |
| Pagos | `pagos.pasarela_pago_id` | `pasarelas_pago.id` | `RESTRICT` |
| Pagos | `pago_eventos.pago_id` | `pagos.id` | `CASCADE` |
| Pagos | `reembolsos.pedido_id` | `pedidos.id` | `CASCADE` |
| Pagos | `reembolsos.pago_id` | `pagos.id` | `CASCADE` |
| Envíos | `envios.pedido_id` | `pedidos.id` | `CASCADE` |
| Envíos | `envios.transportista_id` | `transportistas.id` | `SET NULL` |
| Envíos | `envios.ubicacion_origen_id` | `ubicaciones.id` | `SET NULL` |
| Envíos | `envio_items.envio_id` | `envios.id` | `CASCADE` |
| Envíos | `envio_items.pedido_item_id` | `pedido_items.id` | `CASCADE` |
| Envíos | `envio_eventos.envio_id` | `envios.id` | `CASCADE` |
| Descuentos | `pedido_descuentos.pedido_id` | `pedidos.id` | `CASCADE` |
| Descuentos | `pedido_descuentos.descuento_id` | `descuentos.id` | `RESTRICT` |
| Descuentos | `descuento_productos.descuento_id` | `descuentos.id` | `CASCADE` |
| Descuentos | `descuento_productos.producto_id` | `productos.id` | `CASCADE` |
| Descuentos | `descuento_colecciones.descuento_id` | `descuentos.id` | `CASCADE` |
| Descuentos | `descuento_colecciones.coleccion_id` | `colecciones.id` | `CASCADE` |
| Extras | `resenas.producto_id` | `productos.id` | `SET NULL` |
| Extras | `resenas.cliente_id` | `clientes.id` | `SET NULL` |
| Extras | `listas_deseos.cliente_id` | `clientes.id` | `CASCADE` |
| Extras | `lista_deseo_items.lista_deseo_id` | `listas_deseos.id` | `CASCADE` |
| Extras | `lista_deseo_items.producto_id` | `productos.id` | `CASCADE` |
| Tenants | `tienda_pasarelas.tienda_id` | `tiendas.id` | `CASCADE` |
| Tenants | `tienda_pasarelas.pasarela_pago_id` | `pasarelas_pago.id` | `RESTRICT` |
| Tenants | `tienda_usuarios.tienda_id` | `tiendas.id` | `CASCADE` |
| Tenants | `tienda_usuarios.usuario_id` | `usuarios.id` | `CASCADE` |
| Tenants | `productos.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `clientes.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `pedidos.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `ubicaciones.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `descuentos.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `carritos.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `checkout_sesiones.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `metodos_envio.tienda_id` | `tiendas.id` | `RESTRICT` |
| Tenants | `tasas_impuesto.tienda_id` | `tiendas.id` | `RESTRICT` |

### Límites de las claves foráneas

- `CASCADE` asegura consistencia referencial, no una política segura de conservación. Borrar un pedido también borraría sus líneas, direcciones, historial, pagos, eventos, reembolsos, envíos y descuentos aplicados alcanzables por estas relaciones. La aplicación debe restringir esos borrados según sus obligaciones contables y de auditoría.
- `SET NULL` conserva el registro, pero rompe el enlace con el actor o catálogo original. En pedidos, los datos copiados en `pedido_items` y `pedido_direcciones` son los que preservan el detalle histórico.
- `RESTRICT` aparece en las referencias de pagos a pasarelas y de descuentos aplicados a descuentos. No debe generalizarse a otras relaciones.
- `metacampos.entidad_id` no es clave foránea. La pareja `entidad`/`entidad_id` es polimórfica y necesita validación de existencia, tipo y autorización en el backend.
- Las claves foráneas independientes no verifican pertenencia cruzada: no garantizan que una opción sea del producto de la variante, que un pago y un pedido de un reembolso coincidan, que un envío y su línea sean del mismo pedido, ni que una dirección de checkout pertenezca al CUSTOMER.

## Constraints de unicidad

Todas las tablas tienen `id BIGSERIAL PRIMARY KEY`. Además de las claves primarias, estas son las restricciones `UNIQUE` reales:

| Tabla | Constraint o columna única | Propósito de integridad |
|---|---|---|
| `usuarios` | `email` | Impide dos cuentas con el mismo email según la comparación de PostgreSQL. |
| `roles` | `nombre` | Evita nombres de rol duplicados. |
| `permisos` | `nombre` | Evita nombres de permiso duplicados. |
| `rol_permisos` | `uq_rol_permiso (rol_id, permiso_id)` | Evita asignaciones repetidas. |
| `usuario_roles` | `uq_usuario_rol (usuario_id, rol_id)` | Evita roles repetidos para un usuario. |
| `clientes` | `usuario_id` | Un CUSTOMER por usuario; obligatorio desde V5 (actual pre-B; objetivo B: email por tienda, V20–V23). |
| `sesiones` | `token_hash` | Solo se guarda el hash; el original solo lo tiene el cliente (V8). |
| `usuario_codigos_recuperacion` | `uq_usuario_codigo (usuario_id, codigo_hash)` | Evita códigos duplicados por usuario (V2). |
| `proveedores` | `slug` | Mantiene única la ruta del proveedor. |
| `productos` | `slug` | Mantiene única la ruta del producto. |
| `producto_variantes` | `sku` | Evita SKU duplicados cuando no son nulos. |
| `variante_opcion_valores` | `uq_variante_opcion_valor (variante_id, opcion_valor_id)` | Evita repetir el mismo valor en una variante. |
| `colecciones` | `slug` | Mantiene única la ruta de la colección. |
| `coleccion_productos` | `uq_coleccion_producto (coleccion_id, producto_id)` | Evita repetir un producto en una colección. |
| `etiquetas` | `nombre` y `slug` | Mantiene únicos ambos identificadores comerciales. |
| `producto_etiquetas` | `uq_producto_etiqueta (producto_id, etiqueta_id)` | Evita asignaciones repetidas. |
| `metacampos` | `uq_metacampo (entidad, entidad_id, clave)` | Evita claves repetidas para un mismo objeto lógico. |
| `niveles_inventario` | `uq_nivel_inventario (articulo_inventario_id, ubicacion_id)` | Garantiza un nivel por artículo y ubicación. |
| `carritos` | `token` | Evita tokens de carrito duplicados. |
| `carrito_items` | `uq_carrito_variante (carrito_id, variante_id)` | Garantiza una línea por variante y carrito. |
| `pedidos` | `checkout_sesion_id` | Permite como máximo un pedido por checkout; admite nulo. |
| `pedidos` | `uq_pedido_impuesto (pedido_id, pedido_item_id, titulo)` | Un renglón de impuesto por pedido, línea y título (V9). |
| `pedido_direcciones` | `uq_pedido_direccion_tipo (pedido_id, tipo)` | Una dirección de cada tipo por pedido (V6). |
| `pedido_descuentos` | `uq_pedido_descuento (pedido_id, descuento_id)` | Un código, una vez por pedido (V6). |
| `pagos` | `uq_pago_pasarela_transaccion (pasarela_pago_id, transaccion_externa_id)` | Un cobro externo, una vez por pasarela (V3). |
| `pago_eventos` | `uq_pago_evento_referencia (pago_id, referencia_externa)` | Deduplica webhooks por event-id (V3). |
| `reembolsos` | `uq_reembolso_pago_transaccion (pago_id, transaccion_externa_id)` | Una devolución externa, una vez por pago (V3). |
| `envio_items` | `uq_envio_pedido_item (envio_id, pedido_item_id)` | Una línea, una vez por envío (V6). |
| `resenas` | `uq_resena_producto_cliente (producto_id, cliente_id)` | Una reseña por CUSTOMER y producto (V6). |
| `pedidos` | `numero_pedido` | Mantiene único el número comercial. |
| `pasarelas_pago` | `codigo` | Evita códigos de pasarela duplicados. |
| `transportistas` | `nombre` y `codigo` | Evita transportistas con identificadores duplicados. |
| `tiendas` | `slug` | Mantiene único el identificador textual de la tienda (V10). |
| `tienda_pasarelas` | `uq_tienda_pasarela (tienda_id, pasarela_pago_id)` | Una configuración por tienda y pasarela (V10). |
| `tienda_usuarios` | `uq_tienda_usuario (tienda_id, usuario_id)` | Un rol por usuario y tienda (V12). |
| `descuentos` | `codigo` | Mantiene único el código canjeable. |
| `descuento_productos` | `uq_descuento_producto (descuento_id, producto_id)` | Evita repetir alcance de producto. |
| `descuento_colecciones` | `uq_descuento_coleccion (descuento_id, coleccion_id)` | Evita repetir alcance de colección. |
| `lista_deseo_items` | `uq_lista_deseo_producto (lista_deseo_id, producto_id)` | Evita productos repetidos dentro de una lista. |

La unicidad de combinaciones completas de opciones, una ficha de inventario por variante o números de tracking **no** está declarada. Las unicidades de envío-línea, pedido-descuento, referencias externas de pago y reseña por CUSTOMER-producto sí se declararon en V3/V6. Si el negocio requiere las restantes, debe controlarlas la aplicación o incorporarse mediante una migración futura; no se presenta como existente.

## Constraints CHECK y validaciones pendientes

| Tabla | Garantías reales |
|---|---|
| `usuarios` | `chk_totp_coherente` (V2): si `totp_habilitado = TRUE`, exige secreto y confirmación; `intentos_fallidos >= 0`. |
| `tienda_pasarelas` | `modo` solo admite `test` o `vivo` (V10). |
| `tienda_usuarios` | `rol_tienda` solo admite `admin` u `operador` (V12, actual pre-B; objetivo B: + `store_owner`, V21–V22). |
| `productos` | `estado` solo admite `borrador`, `activo` o `archivado`. |
| `producto_variantes` | `precio >= 0`; `precio_comparativo >= 0` y `peso_gramos >= 0` cuando se informan. |
| `niveles_inventario` | `disponible >= 0` y `reservado >= 0`. |
| `movimientos_inventario` | `cantidad <> 0`; tipo limitado a `entrada`, `salida`, `ajuste`, `reserva` o `liberacion`. |
| `carritos` | Estado limitado a `activo`, `convertido`, `abandonado` o `expirado`. |
| `carrito_items` | `cantidad > 0` y `precio_unitario >= 0`. |
| `metodos_envio` | `precio >= 0`. |
| `tasas_impuesto` | `porcentaje >= 0`. |
| `checkout_sesiones` | Los cinco importes son no negativos; estado limitado a `pendiente`, `completado`, `expirado` o `cancelado`. |
| `pedidos` | Los cinco importes son no negativos; estado limitado a `pendiente`, `pagado`, `en_preparacion`, `enviado`, `entregado`, `cancelado` o `reembolsado`; `chk_archivado_coherente` (V4) exige `archivado_en` cuando `archivado = TRUE`. |
| `pedido_items` | `cantidad > 0`, `precio_unitario >= 0` y `precio_total >= 0`. |
| `pedido_direcciones` | `tipo` limitado a `envio` o `facturacion`. |
| `pagos` | `monto >= 0`; estado limitado a `pendiente`, `procesando`, `aprobado`, `rechazado`, `cancelado` o `reembolsado`. |
| `reembolsos` | `monto > 0`; estado limitado a `pendiente`, `procesando`, `aprobado` o `rechazado`. |
| `envios` | Estado limitado a `pendiente`, `preparando`, `enviado`, `en_transito`, `en_reparto`, `entregado`, `devuelto` o `cancelado`. |
| `envio_items` | `cantidad > 0`. |
| `descuentos` | `valor >= 0`, `uso_maximo > 0` cuando se informa, `usos_actuales >= 0`; tipo limitado a `porcentaje`, `monto_fijo` o `envio_gratis`. |
| `pedido_descuentos` | `monto_aplicado >= 0`. |
| `resenas` | `calificacion` entre 1 y 5 inclusive. |

No existen `CHECK` para los estados de `historial_estados_pedido`, `pago_eventos` o `envio_eventos`, ni para `metacampos.tipo`. Tampoco hay constraints que verifiquen fórmulas de totales, sumas de pagos o reembolsos, sumas de envíos, relación entre stock disponible y reservado, porcentaje máximo de descuento, `usos_actuales <= uso_maximo`, vigencias o transiciones. Esas validaciones son de aplicación y deben ejecutarse con transacciones y control de concurrencia cuando varias solicitudes puedan modificar los mismos datos.

## Inventario completo de los 71 índices explícitos

Los índices siguientes son los 52 `CREATE INDEX` del script V1 más `idx_codigos_recuperacion_usuario` (V2), `idx_sesiones_familia` (V8), `idx_pedido_impuestos_pedido` (V9), `idx_tienda_pasarelas_tienda` (V10), los 9 `idx_*_tienda` (V11), `idx_tienda_usuarios_usuario` (V12), `idx_tokens_cuenta_usuario` (V15), `idx_notificaciones_tienda` (V16), `idx_suscripcion_eventos_suscripcion` (V18) e `idx_checkout_descuentos_checkout` + `idx_checkout_descuentos_descuento` (V19), sin contar los índices que PostgreSQL crea implícitamente para claves primarias y constraints `UNIQUE`. Son índices de búsqueda; por sí solos no añaden unicidad ni autorización.

### Usuarios, CUSTOMERs y auditoría: 7

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_usuarios_email` | `usuarios(email)` | Acelerar búsqueda de cuentas por email. |
| `idx_clientes_usuario` | `clientes(usuario_id)` | Resolver el cliente asociado a un usuario. |
| `idx_direcciones_cliente` | `direcciones(cliente_id)` | Listar direcciones de un cliente. |
| `idx_sesiones_usuario` | `sesiones(usuario_id)` | Localizar sesiones de un usuario. |
| `idx_sesiones_token` | `sesiones(token_legacy)` | Resolver sesiones V1 durante la transición (se elimina en el cutover). |
| `idx_sesiones_familia` | `sesiones(familia_id)` | Revocar y rotar por familia (V8). |
| `idx_codigos_recuperacion_usuario` | `usuario_codigos_recuperacion(usuario_id)` | Localizar códigos de recuperación de un usuario (V2). |
| `idx_auditoria_entidad` | `registros_auditoria(entidad, entidad_id)` | Consultar auditoría de un objeto lógico. |
| `idx_auditoria_usuario` | `registros_auditoria(usuario_id)` | Consultar actividad atribuida a un usuario. |

### Tenants: 11

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_tienda_pasarelas_tienda` | `tienda_pasarelas(tienda_id)` | Configuraciones de pago de una tienda (V10). |
| `idx_tienda_usuarios_usuario` | `tienda_usuarios(usuario_id)` | Tiendas donde un usuario tiene rol (V12). |
| `idx_productos_tienda` | `productos(tienda_id)` | Catálogo de una tienda (V11). |
| `idx_clientes_tienda` | `clientes(tienda_id)` | Clientes de una tienda (V11). |
| `idx_pedidos_tienda` | `pedidos(tienda_id)` | Pedidos de una tienda (V11). |
| `idx_ubicaciones_tienda` | `ubicaciones(tienda_id)` | Sucursales de una tienda (V11). |
| `idx_descuentos_tienda` | `descuentos(tienda_id)` | Descuentos de una tienda (V11). |
| `idx_carritos_tienda` | `carritos(tienda_id)` | Carritos de una tienda (V11). |
| `idx_checkout_tienda` | `checkout_sesiones(tienda_id)` | Checkouts de una tienda (V11). |
| `idx_metodos_envio_tienda` | `metodos_envio(tienda_id)` | Métodos de envío de una tienda (V11). |
| `idx_tasas_impuesto_tienda` | `tasas_impuesto(tienda_id)` | Tasas de una tienda (V11). |

### Catálogo y metacampos: 11

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_productos_proveedor` | `productos(proveedor_id)` | Filtrar productos por proveedor. |
| `idx_productos_estado` | `productos(estado)` | Filtrar catálogo por estado de publicación. |
| `idx_producto_opciones_producto` | `producto_opciones(producto_id)` | Cargar opciones de un producto. |
| `idx_producto_opcion_valores_opcion` | `producto_opcion_valores(producto_opcion_id)` | Cargar valores de una opción. |
| `idx_variantes_producto` | `producto_variantes(producto_id)` | Listar variantes de un producto. |
| `idx_variantes_sku` | `producto_variantes(sku)` | Buscar variantes por SKU. |
| `idx_variante_opcion_valores_variante` | `variante_opcion_valores(variante_id)` | Cargar la composición de una variante. |
| `idx_producto_imagenes_producto` | `producto_imagenes(producto_id)` | Listar imágenes de un producto. |
| `idx_coleccion_productos_producto` | `coleccion_productos(producto_id)` | Encontrar colecciones que contienen un producto. |
| `idx_producto_etiquetas_producto` | `producto_etiquetas(producto_id)` | Cargar etiquetas de un producto. |
| `idx_metacampos_entidad` | `metacampos(entidad, entidad_id)` | Recuperar metacampos de un objeto lógico. |

### Inventario: 4

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_articulos_inventario_variante` | `articulos_inventario(variante_id)` | Resolver artículos de inventario de una variante. |
| `idx_niveles_inventario_articulo` | `niveles_inventario(articulo_inventario_id)` | Consultar niveles de un artículo en sus ubicaciones. |
| `idx_niveles_inventario_ubicacion` | `niveles_inventario(ubicacion_id)` | Consultar existencias de una ubicación. |
| `idx_movimientos_nivel` | `movimientos_inventario(nivel_inventario_id)` | Recuperar movimientos de un nivel. |

### Carrito y checkout: 4

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_carritos_cliente` | `carritos(cliente_id)` | Localizar carritos de un cliente. |
| `idx_carrito_items_carrito` | `carrito_items(carrito_id)` | Cargar líneas de un carrito. |
| `idx_carrito_items_variante` | `carrito_items(variante_id)` | Localizar líneas asociadas a una variante. |
| `idx_checkout_carrito` | `checkout_sesiones(carrito_id)` | Consultar checkouts originados en un carrito. |

### Pedidos: 7

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_pedidos_cliente` | `pedidos(cliente_id)` | Consultar historial de pedidos de un cliente. |
| `idx_pedidos_estado` | `pedidos(estado)` | Filtrar trabajo operativo por estado. |
| `idx_pedido_items_pedido` | `pedido_items(pedido_id)` | Cargar líneas de un pedido. |
| `idx_pedido_items_variante` | `pedido_items(variante_id)` | Localizar líneas históricas vinculadas a una variante. |
| `idx_pedido_direcciones_pedido` | `pedido_direcciones(pedido_id)` | Cargar direcciones snapshot de un pedido. |
| `idx_historial_pedido` | `historial_estados_pedido(pedido_id)` | Recuperar el historial de estados de un pedido. |
| `idx_pedido_impuestos_pedido` | `pedido_impuestos(pedido_id)` | Cargar el snapshot de impuestos de un pedido (V9). |

### Pagos y reembolsos: 5

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_pagos_pedido` | `pagos(pedido_id)` | Consultar pagos e intentos de un pedido. |
| `idx_pagos_pasarela` | `pagos(pasarela_pago_id)` | Consultar operaciones de una pasarela. |
| `idx_pago_eventos_pago` | `pago_eventos(pago_id)` | Recuperar eventos de un pago. |
| `idx_reembolsos_pedido` | `reembolsos(pedido_id)` | Consultar devoluciones de un pedido. |
| `idx_reembolsos_pago` | `reembolsos(pago_id)` | Consultar devoluciones de un pago. |

### Envíos y tracking: 6

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_envios_pedido` | `envios(pedido_id)` | Cargar los envíos de un pedido. |
| `idx_envios_transportista` | `envios(transportista_id)` | Filtrar envíos por transportista. |
| `idx_envios_tracking` | `envios(numero_tracking)` | Buscar un envío por número de tracking. |
| `idx_envio_items_envio` | `envio_items(envio_id)` | Cargar cantidades incluidas en un envío. |
| `idx_envio_items_pedido_item` | `envio_items(pedido_item_id)` | Localizar envíos de una línea de pedido. |
| `idx_envio_eventos_envio` | `envio_eventos(envio_id)` | Recuperar eventos de tracking de un envío. |

### Descuentos: 4

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_pedido_descuentos_pedido` | `pedido_descuentos(pedido_id)` | Consultar descuentos aplicados a un pedido. |
| `idx_pedido_descuentos_descuento` | `pedido_descuentos(descuento_id)` | Consultar usos históricos de un descuento. |
| `idx_descuento_productos_producto` | `descuento_productos(producto_id)` | Resolver descuentos asociados a un producto. |
| `idx_descuento_colecciones_coleccion` | `descuento_colecciones(coleccion_id)` | Resolver descuentos asociados a una colección. |

### Reseñas y listas de deseos: 5

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_resenas_producto` | `resenas(producto_id)` | Listar reseñas de un producto. |
| `idx_resenas_cliente` | `resenas(cliente_id)` | Listar reseñas de un cliente. |
| `idx_listas_deseos_cliente` | `listas_deseos(cliente_id)` | Localizar listas de un cliente. |
| `idx_lista_deseo_items_lista` | `lista_deseo_items(lista_deseo_id)` | Cargar productos de una lista. |
| `idx_lista_deseo_items_producto` | `lista_deseo_items(producto_id)` | Localizar listas que contienen un producto. |

### Tokens, notificaciones y suscripciones: 3

| Índice | Columnas | Propósito |
|---|---|---|
| `idx_tokens_cuenta_usuario` | `tokens_cuenta(usuario_id)` | Localizar tokens de verificación y reset de un usuario (V15). |
| `idx_notificaciones_tienda` | `notificaciones(tienda_id)` | Bandeja de avisos de una tienda (V16). |
| `idx_suscripcion_eventos_suscripcion` | `suscripcion_eventos(suscripcion_id)` | Deduplicar eventos de facturación (V18). |
| `idx_checkout_descuentos_checkout` | `checkout_descuentos(checkout_sesion_id)` | Códigos aplicados a un checkout (V19). |
| `idx_checkout_descuentos_descuento` | `checkout_descuentos(descuento_id)` | Usos de un descuento en checkouts (V19). |

## Auditoría

`registros_auditoria` permite registrar `usuario_id`, `entidad`, `entidad_id`, `accion`, `datos_anteriores`, `datos_nuevos`, `ip` y `creado_en`. Los cambios JSONB permiten conservar el contexto anterior y posterior, y `ON DELETE SET NULL` conserva el evento aunque se elimine el usuario.

**No existen triggers de auditoría en el script.** Insertar una venta, cambiar un estado o modificar un rol no produce por sí solo un registro. Spring Boot debe crear explícitamente la entrada, idealmente en la misma transacción local que el cambio, incluir el actor autenticado y evitar registrar contraseñas, tokens, secretos de pasarela o datos personales innecesarios. La referencia lógica `entidad`/`entidad_id` tampoco tiene clave foránea; su coherencia depende de la aplicación.

El esquema tampoco impide actualizar o borrar auditoría. Si se requiere un registro de solo anexado, la limitación de privilegios y la política de retención deben configurarse como controles adicionales, no darse por existentes.

## Riesgos principales de integridad

| Riesgo | Consecuencia | Control necesario |
|---|---|---|
| Relaciones polimórficas y referencias externas sin unicidad | Metacampos huérfanos, referencias ambiguas o eventos duplicados. | Validación de destino y deduplicación transaccional en aplicación. |
| Estados históricos y externos sin `CHECK` | Valores incompatibles con los estados normalizados. | Vocabulario controlado y mapeo explícito antes de actualizar columnas con enum. |
| Ausencia de invariantes agregados o cruzados | Sobrepago, sobre reembolso, envíos excesivos o relaciones de otro pedido. | Bloqueos, validaciones de pertenencia y sumas dentro de transacciones. |
| Cascadas sobre datos históricos | Pérdida encadenada de información operativa o financiera. | Autorización estricta y política de conservación antes de cualquier borrado. |
| Totales, contadores y fechas de actualización sin automatización | Datos desactualizados o incoherentes tras errores de aplicación. | Cálculo y actualización en la misma unidad transaccional; pruebas de concurrencia. |

## Recomendaciones para el backend y Supabase

Las siguientes medidas son recomendaciones pendientes de implementación, no características que el SQL ya active:

1. **Contraseñas y autenticación**: usar Argon2id o BCrypt con parámetros adecuados, comparación segura, respuestas de login genéricas, límites de intentos y validación de sesión en un filtro de Spring Security. Rotar el token tras autenticar y revocar sesiones al cerrar o deshabilitar la cuenta. Desde V8 solo se guarda `sesiones.token_hash` (SHA-256 del refresh); el valor original únicamente lo tiene el titular. Objetivo B: CUSTOMER con HMAC y sesiones propias (V21).
2. **Autorización**: aplicar RBAC canónico (ADR-016) en el servidor con denegación por defecto y comprobaciones de titularidad por recurso. No confiar en roles, precios, totales, estados o identificadores de CUSTOMER enviados por el navegador.
3. **Transacciones y concurrencia**: agrupar cambios dependientes y usar bloqueos de fila o actualizaciones condicionales para checkout, stock, conversión del pedido, pagos, reembolsos y consumo de descuentos. Las APIs externas quedan fuera de la transacción PostgreSQL y necesitan reconciliación.
4. **Acceso a datos**: usar consultas parametrizadas mediante JPA/JDBC, validar entradas y devolver solo los campos necesarios. Las validaciones de Bean Validation complementan, pero no sustituyen, los constraints de PostgreSQL.
5. **Credenciales de infraestructura**: mantener contraseñas de base, claves de Supabase y secretos de pago o tracking en variables o un gestor de secretos. Una clave de servicio de Supabase debe permanecer exclusivamente en el backend; no se afirma que RLS esté configurada por este script.
6. **Exposición de Supabase**: si algún consumidor accede directamente a PostgREST o Storage, definir y probar RLS y políticas específicas antes de exponerlo. Si todo acceso pasa por Spring Boot, usar igualmente una cuenta de base con privilegios mínimos y no otorgar a la aplicación permisos innecesarios de administración.
7. **Integraciones externas**: exigir TLS, verificar firmas o autenticación de webhooks, validar que la referencia corresponda a la pasarela y operación esperadas, aplicar tiempos de espera y hacer reintentos idempotentes. No registrar secretos completos en `pago_eventos.payload` ni en logs.
8. **Protección de datos**: cifrar el transporte, restringir CORS y orígenes confiables, limitar tamaño de payloads, anonimizar logs y definir retención para sesiones, carritos, eventos y auditoría conforme a la necesidad del negocio.
9. **Verificación**: añadir pruebas de constraints y de autorización, además de casos concurrentes para doble checkout, stock insuficiente, eventos repetidos, envíos que exceden cantidades y descuentos con último uso disponible.

Cualquier nuevo constraint, trigger, política RLS o tabla de soporte que se decida a partir de estas recomendaciones requerirá una migración y documentación posterior; no forma parte del esquema actual.
