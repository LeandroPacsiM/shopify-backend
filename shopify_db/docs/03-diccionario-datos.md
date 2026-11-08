# Diccionario de datos de Shopify DB

Este documento describe las 58 tablas (V1 en `database/01_esquema_shopify.sql` + `usuario_codigos_recuperacion` de V2 + `pedido_impuestos` de V9 + `tiendas`, `tienda_pasarelas` y `tienda_usuarios` de V10–V12 + `tokens_cuenta` de V15 + `notificaciones` de V16 + `suscripciones` y `suscripcion_eventos` de V18) para PostgreSQL. Las tablas se presentan en el mismo orden del esquema y cada columna conserva su tipo, nulabilidad, valores predeterminados y restricciones declaradas. Los cambios de las migraciones V2–V18 se indican con su versión.

## Convenciones

- **PK** identifica la clave primaria. **FK** identifica una clave foránea e indica la tabla y columna referenciadas, además de su acción real `ON DELETE`.
- **UNIQUE** indica una restricción de unicidad simple; **UNIQUE compuesto** indica una restricción que abarca varias columnas y muestra su nombre cuando está declarado.
- La columna **Nulo** usa **Sí** cuando el campo admite valores `NULL` y **No** cuando es `NOT NULL` o forma parte de la clave primaria.
- **DEFAULT** reproduce el valor o la expresión predeterminada del esquema. **CHECK** reproduce la condición de validación; si la restricción tiene nombre, se incluye.
- `BIGSERIAL` genera valores de tipo `BIGINT` mediante una secuencia implícita. Por ello, los identificadores `BIGSERIAL` se documentan con ese tipo exacto y las referencias hacia ellos usan `BIGINT`.
- Un guion largo (—) en **Restricciones** significa que el campo no tiene una restricción adicional que deba consignarse en esa celda.

## usuarios

**Descripción:** Almacena las cuentas con acceso al sistema, ya sean administradores, operadores o clientes, junto con sus credenciales y datos básicos.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador interno de la cuenta de usuario. |
| email | VARCHAR(255) | UNIQUE | No | Correo electrónico de acceso, único en el sistema. |
| password_hash | VARCHAR(255) | — | No | Hash de contraseña generado por el backend; el esquema no define el algoritmo. |
| nombre | VARCHAR(100) | — | Sí | Nombre de pila del usuario. |
| apellido | VARCHAR(100) | — | Sí | Apellido del usuario. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la cuenta puede utilizarse. |
| email_verificado | BOOLEAN | DEFAULT FALSE | No | Indica si el email fue verificado. Añadida en V2. |
| totp_secret | TEXT | — | Sí | Secreto TOTP cifrado (llave en entorno). NULL hasta enrolar. Añadida en V2. |
| totp_habilitado | BOOLEAN | DEFAULT FALSE | No | Indica si el 2FA está activo. Añadida en V2. |
| totp_confirmado_en | TIMESTAMPTZ | — | Sí | Cuándo se validó el primer código. Añadida en V2. |
| intentos_fallidos | INTEGER | DEFAULT 0; CHECK (intentos_fallidos >= 0) | No | Intentos fallidos de auth/2FA. Añadida en V2. |
| bloqueado_hasta | TIMESTAMPTZ | — | Sí | Bloqueo por fuerza bruta. Añadida en V2. |
| ultimo_acceso | TIMESTAMPTZ | — | Sí | Último acceso exitoso. Añadida en V2. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación de la cuenta. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización registrada. |

## roles

**Descripción:** Define los perfiles de autorización que pueden asignarse a los usuarios.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del rol. |
| nombre | VARCHAR(50) | UNIQUE | No | Nombre único que identifica funcionalmente al rol. |
| descripcion | VARCHAR(255) | — | Sí | Explicación de las responsabilidades o el alcance del rol. |

## permisos

**Descripción:** Contiene el catálogo de permisos individuales que pueden agruparse en roles.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del permiso. |
| nombre | VARCHAR(100) | UNIQUE | No | Nombre único de la capacidad autorizada. |
| descripcion | VARCHAR(255) | — | Sí | Detalle de la acción o recurso comprendido por el permiso. |

## rol_permisos

**Descripción:** Relaciona roles con permisos e impide asignar dos veces el mismo permiso a un rol.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asignación. |
| rol_id | BIGINT | FK a roles(id) ON DELETE CASCADE; UNIQUE compuesto uq_rol_permiso (rol_id, permiso_id) | No | Rol que recibe el permiso. |
| permiso_id | BIGINT | FK a permisos(id) ON DELETE CASCADE; UNIQUE compuesto uq_rol_permiso (rol_id, permiso_id) | No | Permiso incorporado al rol. |

## usuario_roles

**Descripción:** Asigna roles a las cuentas de usuario sin permitir asignaciones duplicadas.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asignación. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE CASCADE; UNIQUE compuesto uq_usuario_rol (usuario_id, rol_id) | No | Usuario al que se concede el rol. |
| rol_id | BIGINT | FK a roles(id) ON DELETE CASCADE; UNIQUE compuesto uq_usuario_rol (usuario_id, rol_id) | No | Rol concedido al usuario. |

## clientes

**Descripción:** Guarda la información comercial del comprador, vinculada obligatoriamente a una cuenta de usuario desde V5 (sin guest checkout).

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del cliente. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| usuario_id | BIGINT | UNIQUE; FK a usuarios(id) ON DELETE RESTRICT | No | Cuenta de usuario asociada; el vínculo es único y obligatorio (V5). Borrar el usuario se bloquea. |
| nombre | VARCHAR(100) | — | Sí | Nombre comercial o de pila del comprador. |
| apellido | VARCHAR(100) | — | Sí | Apellido del comprador. |
| telefono | VARCHAR(30) | — | Sí | Número telefónico de contacto. |
| acepta_marketing | BOOLEAN | DEFAULT FALSE | No | Indica si el cliente autorizó comunicaciones de mercadotecnia. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro del cliente. |

## direcciones

**Descripción:** Registra las direcciones postales asociadas a cada cliente, con la posibilidad de marcar una como principal.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la dirección. |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE CASCADE | No | Cliente propietario de la dirección. |
| linea1 | VARCHAR(255) | — | No | Primera línea de la dirección postal. |
| linea2 | VARCHAR(255) | — | Sí | Información complementaria, como interior, piso o referencia. |
| ciudad | VARCHAR(100) | — | Sí | Ciudad o localidad. |
| departamento | VARCHAR(100) | — | Sí | Departamento, estado o región administrativa. |
| codigo_postal | VARCHAR(20) | — | Sí | Código postal de la dirección. |
| pais | VARCHAR(100) | — | Sí | País de la dirección. |
| es_principal | BOOLEAN | DEFAULT FALSE | No | Señala si esta es la dirección principal del cliente. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro de la dirección. |

## sesiones

**Descripción:** Resume las sesiones de autenticación de los usuarios y su vigencia.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la sesión. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE CASCADE | No | Usuario autenticado. |
| token_hash | VARCHAR(255) | UNIQUE | Sí | SHA-256 del refresh opaco; el original solo lo tiene el cliente. Añadida en V8 (NULL hasta el cutover). |
| token_legacy | VARCHAR(255) | UNIQUE | Sí | Token V1 en claro, en transición; se elimina tras el cutover (V8, nullable desde V13). |
| familia_id | UUID | — | Sí | Cadena de rotación del login. Añadida en V8. |
| revocado_en | TIMESTAMPTZ | — | Sí | Revocación (logout, rotación, robo). Añadida en V8. |
| reemplazado_por_id | BIGINT | FK a sesiones(id) ON DELETE SET NULL | Sí | Siguiente sesión de la cadena. Añadida en V8. |
| motivo_revocacion | VARCHAR(50) | — | Sí | Motivo de la revocación. Añadida en V8. |
| ip | VARCHAR(45) | — | Sí | Dirección IP de origen. |
| user_agent | VARCHAR(255) | — | Sí | Identificación del cliente o navegador. |
| expira_en | TIMESTAMPTZ | — | No | Momento de expiración de la sesión. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Momento de creación de la sesión. |

## usuario_codigos_recuperacion

**Descripción:** Códigos de recuperación 2FA de un solo uso. Tabla añadida en la migración V2.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del código. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE CASCADE; UNIQUE compuesto uq_usuario_codigo (usuario_id, codigo_hash) | No | Usuario dueño del código. |
| codigo_hash | VARCHAR(255) | UNIQUE compuesto uq_usuario_codigo (usuario_id, codigo_hash) | No | Hash BCrypt/Argon2 del código; el original se muestra una sola vez. |
| usado_en | TIMESTAMPTZ | — | Sí | Cuándo se consumió; NULL mientras esté vigente. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de emisión. |

## tokens_cuenta

**Descripción:** Tokens de un solo uso para verificación de email y reset de contraseña. Tabla añadida en la migración V15.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del token. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE CASCADE | No | Usuario dueño del token. |
| tipo | VARCHAR(30) | CHECK (verificacion_email, reset_password) | No | Uso del token. |
| token_hash | VARCHAR(255) | UNIQUE | No | SHA-256 del token opaco; el plano solo viaja por correo. |
| expira_en | TIMESTAMPTZ | — | No | Vencimiento (verificación 24h, reset 60min; lo impone el backend). |
| usado_en | TIMESTAMPTZ | — | Sí | Cuándo se consumió/invalidó; NULL mientras esté vigente. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de emisión. |

## notificaciones

**Descripción:** Avisos persistentes de la bandeja admin, uno por evento. Tabla añadida en la migración V16. El WS solo avisa en vivo; esta tabla es la fuente que la bandeja lee.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del aviso. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda dueña del aviso (bandeja por tienda). |
| tipo | VARCHAR(50) | — | No | Evento (nuevo_pedido, pedido_pagado, pedido_cancelado, pedido_entregado, stock_bajo). |
| entidad | VARCHAR(100) | — | Sí | Referencia lógica (p. ej. `pedido`); sin FK. |
| entidad_id | BIGINT | — | Sí | Id lógico del registro referido. |
| datos | JSONB | — | Sí | Datos del aviso (ids, número, total); sin secretos. |
| leida_en | TIMESTAMPTZ | — | Sí | Cuándo se leyó; NULL mientras esté sin leer. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora del evento. |

## registros_auditoria

**Descripción:** Resume los cambios auditados sobre entidades, conservando opcionalmente sus estados anterior y nuevo.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del registro de auditoría. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE SET NULL | Sí | Usuario que realizó la acción. |
| entidad | VARCHAR(100) | — | No | Nombre de la entidad afectada. |
| entidad_id | BIGINT | — | Sí | Identificador lógico del registro afectado. |
| accion | VARCHAR(50) | — | No | Acción ejecutada sobre la entidad. |
| datos_anteriores | JSONB | — | Sí | Datos previos a la modificación. |
| datos_nuevos | JSONB | — | Sí | Datos posteriores a la modificación. |
| ip | VARCHAR(45) | — | Sí | Dirección IP desde la que se originó la acción. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Momento en que se registró la auditoría. |

## proveedores

**Descripción:** Resume los proveedores que abastecen los productos del catálogo.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del proveedor. |
| nombre | VARCHAR(255) | — | No | Nombre del proveedor. |
| slug | VARCHAR(255) | UNIQUE | No | Identificador textual único para URLs. |
| descripcion | TEXT | — | Sí | Información descriptiva del proveedor. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si el proveedor está habilitado. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro. |

## productos

**Descripción:** Define el catálogo general de productos, su presentación pública y su ciclo de publicación.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del producto. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| proveedor_id | BIGINT | FK a proveedores(id) ON DELETE SET NULL | Sí | Proveedor asociado; se conserva el producto sin proveedor si este se elimina. |
| titulo | VARCHAR(255) | — | No | Nombre visible del producto. |
| slug | VARCHAR(255) | UNIQUE | No | Identificador textual único empleado en la URL del producto. |
| descripcion | TEXT | — | Sí | Descripción comercial detallada. |
| estado | VARCHAR(20) | DEFAULT 'borrador'; chk_producto_estado CHECK (estado IN ('borrador', 'activo', 'archivado')) | No | Situación editorial del producto dentro del catálogo. |
| publicado_en | TIMESTAMPTZ | — | Sí | Momento en que el producto fue publicado. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización. |

## producto_opciones

**Descripción:** Declara las dimensiones variables de un producto, como talla o color, y su orden de presentación.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la opción del producto. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE | No | Producto al que pertenece la opción. |
| nombre | VARCHAR(100) | — | No | Nombre de la dimensión variable. |
| posicion | INTEGER | DEFAULT 0 | No | Orden relativo de presentación de la opción. |

## producto_opcion_valores

**Descripción:** Contiene los valores posibles de cada opción de producto, por ejemplo, cada talla o color disponible.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del valor de opción. |
| producto_opcion_id | BIGINT | FK a producto_opciones(id) ON DELETE CASCADE | No | Opción a la que pertenece el valor. |
| valor | VARCHAR(100) | — | No | Texto del valor seleccionable. |
| posicion | INTEGER | DEFAULT 0 | No | Orden relativo de presentación del valor. |

## producto_variantes

**Descripción:** Representa las versiones comprables de un producto, con su combinación de opciones, precio, SKU y datos logísticos.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la variante. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE | No | Producto general del que deriva la variante. |
| sku | VARCHAR(100) | UNIQUE | Sí | Código de inventario opcional y único para la variante. |
| codigo_barras | VARCHAR(100) | — | Sí | Código de barras asociado a la unidad comercial. |
| precio | NUMERIC(12,2) | CHECK (precio >= 0) | No | Precio de venta vigente de la variante. |
| precio_comparativo | NUMERIC(12,2) | CHECK (precio_comparativo >= 0) | Sí | Precio de referencia utilizado para mostrar comparaciones o rebajas. |
| peso_gramos | INTEGER | CHECK (peso_gramos >= 0) | Sí | Peso de la variante expresado en gramos. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la variante está habilitada para su venta. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación de la variante. |

## variante_opcion_valores

**Descripción:** Define qué valores de opción componen cada variante y evita repetir el mismo valor dentro de una variante.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la relación. |
| variante_id | BIGINT | FK a producto_variantes(id) ON DELETE CASCADE; UNIQUE compuesto uq_variante_opcion_valor (variante_id, opcion_valor_id) | No | Variante que se está configurando. |
| opcion_valor_id | BIGINT | FK a producto_opcion_valores(id) ON DELETE CASCADE; UNIQUE compuesto uq_variante_opcion_valor (variante_id, opcion_valor_id) | No | Valor de opción incorporado a la variante. |

## producto_imagenes

**Descripción:** Administra las imágenes del catálogo, su orden y su eventual asociación con una variante específica.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la imagen. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE | No | Producto al que pertenece la imagen. |
| variante_id | BIGINT | FK a producto_variantes(id) ON DELETE SET NULL | Sí | Variante específica ilustrada por la imagen, si corresponde. |
| url | VARCHAR(500) | — | No | Ubicación pública o interna del recurso gráfico. |
| texto_alt | VARCHAR(255) | — | Sí | Texto alternativo para accesibilidad y contexto de la imagen. |
| posicion | INTEGER | DEFAULT 0 | No | Orden de presentación dentro de la galería. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro de la imagen. |

## colecciones

**Descripción:** Agrupa productos bajo criterios comerciales o editoriales y controla su disponibilidad pública.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la colección. |
| titulo | VARCHAR(255) | — | No | Nombre visible de la colección. |
| slug | VARCHAR(255) | UNIQUE | No | Identificador textual único para la URL. |
| descripcion | TEXT | — | Sí | Texto que explica el propósito o contenido de la colección. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la colección está habilitada. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |

## coleccion_productos

**Descripción:** Establece la pertenencia de productos a colecciones, su orden y la unicidad de cada asociación.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la relación. |
| coleccion_id | BIGINT | FK a colecciones(id) ON DELETE CASCADE; UNIQUE compuesto uq_coleccion_producto (coleccion_id, producto_id) | No | Colección que incorpora el producto. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE; UNIQUE compuesto uq_coleccion_producto (coleccion_id, producto_id) | No | Producto incluido en la colección. |
| posicion | INTEGER | DEFAULT 0 | No | Orden del producto dentro de la colección. |

## etiquetas

**Descripción:** Resume el catálogo de etiquetas utilizables para clasificar productos.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la etiqueta. |
| nombre | VARCHAR(100) | UNIQUE | No | Nombre único de la etiqueta. |
| slug | VARCHAR(100) | UNIQUE | No | Identificador textual único para URLs. |

## producto_etiquetas

**Descripción:** Resume la clasificación de productos mediante etiquetas, sin duplicar una misma asociación.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asociación. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE; UNIQUE compuesto uq_producto_etiqueta (producto_id, etiqueta_id) | No | Producto clasificado. |
| etiqueta_id | BIGINT | FK a etiquetas(id) ON DELETE CASCADE; UNIQUE compuesto uq_producto_etiqueta (producto_id, etiqueta_id) | No | Etiqueta asignada. |

## metacampos

**Descripción:** Resume atributos extensibles asociados lógicamente a cualquier entidad identificada por nombre e identificador.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del metacampo. |
| entidad | VARCHAR(50) | UNIQUE compuesto uq_metacampo (entidad, entidad_id, clave) | No | Tipo lógico de entidad a la que se añade información. |
| entidad_id | BIGINT | UNIQUE compuesto uq_metacampo (entidad, entidad_id, clave) | No | Identificador lógico de la entidad; no declara una clave foránea. |
| clave | VARCHAR(100) | UNIQUE compuesto uq_metacampo (entidad, entidad_id, clave) | No | Nombre del atributo adicional. |
| valor | TEXT | — | Sí | Contenido almacenado para el atributo. |
| tipo | VARCHAR(30) | DEFAULT 'texto' | No | Clasificación del contenido del metacampo. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |

## ubicaciones

**Descripción:** Define los puntos físicos desde los que se controla inventario o se originan envíos.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la ubicación. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| nombre | VARCHAR(255) | — | No | Nombre de la tienda, almacén u otro punto logístico. |
| direccion | VARCHAR(255) | — | Sí | Dirección postal principal de la ubicación. |
| ciudad | VARCHAR(100) | — | Sí | Ciudad donde se encuentra la ubicación. |
| pais | VARCHAR(100) | — | Sí | País donde se encuentra la ubicación. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la ubicación está operativa. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro. |

## articulos_inventario

**Descripción:** Representa la configuración de inventario de una variante y sus reglas de envío y control de existencias.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del artículo de inventario. |
| variante_id | BIGINT | FK a producto_variantes(id) ON DELETE CASCADE | No | Variante cuyas existencias se administran. |
| sku | VARCHAR(100) | — | Sí | SKU operativo conservado para la gestión de inventario. |
| requiere_envio | BOOLEAN | DEFAULT TRUE | No | Indica si el artículo necesita entrega física. |
| controla_stock | BOOLEAN | DEFAULT TRUE | No | Indica si las existencias deben controlarse para este artículo. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación del artículo de inventario. |

## niveles_inventario

**Descripción:** Mantiene el stock disponible y reservado de cada artículo en una ubicación determinada.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del nivel de inventario. |
| articulo_inventario_id | BIGINT | FK a articulos_inventario(id) ON DELETE CASCADE; UNIQUE compuesto uq_nivel_inventario (articulo_inventario_id, ubicacion_id) | No | Artículo cuyo stock se registra. |
| ubicacion_id | BIGINT | FK a ubicaciones(id) ON DELETE RESTRICT; UNIQUE compuesto uq_nivel_inventario (articulo_inventario_id, ubicacion_id) | No | Ubicación física del stock. No puede eliminarse mientras tenga niveles (V4). |
| disponible | INTEGER | DEFAULT 0; CHECK (disponible >= 0) | No | Cantidad libre para venta o asignación. |
| reservado | INTEGER | DEFAULT 0; CHECK (reservado >= 0) | No | Cantidad comprometida y no disponible para otras operaciones. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización del nivel. |

## movimientos_inventario

**Descripción:** Registra los ajustes y movimientos que explican los cambios de stock de un nivel de inventario.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del movimiento. |
| nivel_inventario_id | BIGINT | FK a niveles_inventario(id) ON DELETE CASCADE | No | Nivel de inventario afectado. |
| tipo_movimiento | VARCHAR(30) | chk_tipo_movimiento CHECK (tipo_movimiento IN ('entrada', 'salida', 'ajuste', 'reserva', 'liberacion')) | No | Naturaleza del movimiento de existencias. |
| cantidad | INTEGER | CHECK (cantidad <> 0) | No | Cantidad movida, con signo según la operación; no puede ser cero. |
| motivo | VARCHAR(255) | — | Sí | Explicación funcional del movimiento. |
| referencia | VARCHAR(100) | — | Sí | Referencia externa o interna relacionada con el movimiento. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE SET NULL | Sí | Usuario responsable del movimiento, si se conoce. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro del movimiento. |

## carritos

**Descripción:** Conserva los carritos de compra, su propietario opcional, moneda, vigencia y estado de conversión.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del carrito. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE SET NULL | Sí | Cliente propietario; permite carritos sin cliente identificado. |
| token | VARCHAR(255) | UNIQUE | No | Token único para localizar el carrito. |
| moneda | CHAR(3) | DEFAULT 'PEN' | No | Código de moneda de tres caracteres usado en el carrito. |
| estado | VARCHAR(20) | DEFAULT 'activo'; chk_carrito_estado CHECK (estado IN ('activo', 'convertido', 'abandonado', 'expirado')) | No | Situación actual del carrito dentro del proceso de compra. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización. |
| expira_en | TIMESTAMPTZ | — | Sí | Momento a partir del cual el carrito deja de estar vigente. |

## carrito_items

**Descripción:** Contiene las variantes seleccionadas en cada carrito, sus cantidades y el precio unitario capturado.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la línea del carrito. |
| carrito_id | BIGINT | FK a carritos(id) ON DELETE CASCADE; UNIQUE compuesto uq_carrito_variante (carrito_id, variante_id) | No | Carrito al que pertenece la línea. |
| variante_id | BIGINT | FK a producto_variantes(id) ON DELETE CASCADE; UNIQUE compuesto uq_carrito_variante (carrito_id, variante_id) | No | Variante seleccionada; solo puede aparecer una vez por carrito. |
| cantidad | INTEGER | CHECK (cantidad > 0) | No | Número de unidades seleccionadas. |
| precio_unitario | NUMERIC(12,2) | CHECK (precio_unitario >= 0) | No | Precio por unidad registrado en el carrito. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora en que se añadió la línea. |

## metodos_envio

**Descripción:** Resume las modalidades de entrega ofrecidas durante el checkout.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del método de envío. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| nombre | VARCHAR(100) | — | No | Nombre visible del método. |
| descripcion | VARCHAR(255) | — | Sí | Explicación de la modalidad de entrega. |
| precio | NUMERIC(12,2) | CHECK (precio >= 0) | No | Costo del método de envío. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si el método está disponible. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro. |

## tasas_impuesto

**Descripción:** Resume las tasas tributarias configurables por ámbito geográfico.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la tasa. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| nombre | VARCHAR(100) | — | No | Nombre del impuesto o tasa. |
| pais | VARCHAR(100) | — | Sí | País al que se aplica. |
| departamento | VARCHAR(100) | — | Sí | Departamento o región al que se aplica. |
| porcentaje | NUMERIC(5,2) | CHECK (porcentaje >= 0) | No | Porcentaje tributario aplicable. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la tasa está habilitada. |

## checkout_sesiones

**Descripción:** Reúne los datos y totales de un intento de checkout, incluidas las direcciones y el método de envío elegidos.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la sesión de checkout. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| carrito_id | BIGINT | FK a carritos(id) ON DELETE CASCADE | No | Carrito que origina el checkout. |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE RESTRICT | No | Cliente que realiza el checkout; obligatorio desde V5 (sin guest). |
| direccion_envio_id | BIGINT | FK a direcciones(id) ON DELETE SET NULL | Sí | Dirección seleccionada para la entrega. |
| direccion_facturacion_id | BIGINT | FK a direcciones(id) ON DELETE SET NULL | Sí | Dirección seleccionada para la facturación. |
| metodo_envio_id | BIGINT | FK a metodos_envio(id) ON DELETE SET NULL | Sí | Método de entrega elegido. |
| moneda | CHAR(3) | DEFAULT 'PEN' | No | Moneda en la que se calculan los importes del checkout. |
| subtotal | NUMERIC(12,2) | DEFAULT 0; CHECK (subtotal >= 0) | No | Suma de los artículos antes de impuestos, envío y descuentos. |
| impuesto_total | NUMERIC(12,2) | DEFAULT 0; CHECK (impuesto_total >= 0) | No | Importe total de impuestos calculado. |
| envio_total | NUMERIC(12,2) | DEFAULT 0; CHECK (envio_total >= 0) | No | Costo total de entrega. |
| descuento_total | NUMERIC(12,2) | DEFAULT 0; CHECK (descuento_total >= 0) | No | Importe total de descuentos aplicados. |
| total | NUMERIC(12,2) | DEFAULT 0; CHECK (total >= 0) | No | Importe final calculado para el checkout. |
| estado | VARCHAR(30) | DEFAULT 'pendiente'; chk_checkout_estado CHECK (estado IN ('pendiente', 'completado', 'expirado', 'cancelado')) | No | Situación del intento de checkout. |
| expira_en | TIMESTAMPTZ | — | Sí | Momento de expiración de la sesión. |
| email_contacto | VARCHAR(255) | — | Sí | Email de contacto dejado por el cliente (V17). |
| telefono_contacto | VARCHAR(50) | — | Sí | Teléfono de contacto (V17). |
| notas_cliente | VARCHAR(1000) | — | Sí | Notas del cliente para el pedido (V17). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de inicio del checkout. |

## pedidos

**Descripción:** Registra las compras confirmadas, sus importes, moneda y situación dentro del ciclo de atención.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador interno del pedido. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE RESTRICT | No | Cliente que realizó la compra; obligatorio desde V5. Borrarlo se bloquea. |
| checkout_sesion_id | BIGINT | UNIQUE; FK a checkout_sesiones(id) ON DELETE SET NULL | Sí | Sesión de checkout de origen, con una relación única y opcional. |
| numero_pedido | VARCHAR(30) | UNIQUE | No | Número comercial único visible para identificar el pedido. |
| estado | VARCHAR(30) | DEFAULT 'pendiente'; chk_pedido_estado CHECK (estado IN ('pendiente', 'pagado', 'en_preparacion', 'enviado', 'entregado', 'cancelado', 'reembolsado')) | No | Estado actual del pedido. |
| moneda | CHAR(3) | DEFAULT 'PEN' | No | Moneda de los importes registrados. |
| subtotal | NUMERIC(12,2) | DEFAULT 0; CHECK (subtotal >= 0) | No | Valor de los artículos antes de los demás cargos y descuentos. |
| impuesto_total | NUMERIC(12,2) | DEFAULT 0; CHECK (impuesto_total >= 0) | No | Total de impuestos del pedido. |
| envio_total | NUMERIC(12,2) | DEFAULT 0; CHECK (envio_total >= 0) | No | Total cobrado por envío. |
| descuento_total | NUMERIC(12,2) | DEFAULT 0; CHECK (descuento_total >= 0) | No | Total de descuentos concedidos. |
| total | NUMERIC(12,2) | DEFAULT 0; CHECK (total >= 0) | No | Importe final del pedido. |
| email_contacto | VARCHAR(255) | — | Sí | Snapshot del contacto del checkout (V17). |
| telefono_contacto | VARCHAR(50) | — | Sí | Snapshot del teléfono del checkout (V17). |
| notas_cliente | VARCHAR(1000) | — | Sí | Snapshot de las notas del checkout (V17). |
| realizado_en | TIMESTAMPTZ | — | Sí | Momento en que se efectuó el pedido. |
| archivado | BOOLEAN | DEFAULT FALSE | No | Archivado lógico (V4). No se borran pedidos con historial. |
| archivado_en | TIMESTAMPTZ | — | Sí | Cuándo se archivó; obligatorio si archivado=TRUE (V4). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación del registro. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización. |

## pedido_items

**Descripción:** Conserva las líneas de venta y una instantánea histórica de los datos del producto y la variante comprados.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la línea del pedido. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE | No | Pedido al que pertenece la línea. |
| variante_id | BIGINT | FK a producto_variantes(id) ON DELETE SET NULL | Sí | Variante originalmente comprada; la línea histórica se conserva si esta se elimina. |
| producto_titulo | VARCHAR(255) | — | No | Título del producto capturado al momento de la venta. |
| variante_titulo | VARCHAR(255) | — | Sí | Denominación de la variante al momento de la venta. |
| sku | VARCHAR(100) | — | Sí | SKU histórico de la unidad vendida. |
| cantidad | INTEGER | CHECK (cantidad > 0) | No | Número de unidades vendidas. |
| precio_unitario | NUMERIC(12,2) | CHECK (precio_unitario >= 0) | No | Precio por unidad aplicado en la venta. |
| precio_total | NUMERIC(12,2) | CHECK (precio_total >= 0) | No | Importe total registrado para la línea. |

## pedido_direcciones

**Descripción:** Guarda copias de las direcciones de envío y facturación utilizadas en cada pedido.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la dirección histórica. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE; UNIQUE compuesto uq_pedido_direccion_tipo (pedido_id, tipo) | No | Pedido al que pertenece la dirección. |
| tipo | VARCHAR(20) | chk_tipo_direccion CHECK (tipo IN ('envio', 'facturacion')); UNIQUE compuesto uq_pedido_direccion_tipo (pedido_id, tipo) | No | Distingue si la dirección se usa para envío o facturación. Una de cada tipo por pedido (V6). |
| nombre_destinatario | VARCHAR(150) | — | Sí | Nombre de la persona receptora o titular. |
| linea1 | VARCHAR(255) | — | Sí | Primera línea de la dirección. |
| linea2 | VARCHAR(255) | — | Sí | Complemento de la dirección. |
| ciudad | VARCHAR(100) | — | Sí | Ciudad o localidad. |
| departamento | VARCHAR(100) | — | Sí | Departamento, estado o región. |
| codigo_postal | VARCHAR(20) | — | Sí | Código postal. |
| pais | VARCHAR(100) | — | Sí | País de la dirección. |
| telefono | VARCHAR(30) | — | Sí | Teléfono de contacto asociado a la entrega o facturación. |

## pedido_impuestos

**Descripción:** Snapshot de impuestos por pedido y línea, al estilo de las tax lines de Shopify. Tabla añadida en la migración V9; `tasas_impuesto` queda como catálogo vivo para cotizar.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del renglón de impuesto. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE; UNIQUE compuesto uq_pedido_impuesto (pedido_id, pedido_item_id, titulo) | No | Pedido al que pertenece el impuesto. |
| pedido_item_id | BIGINT | FK a pedido_items(id) ON DELETE CASCADE; UNIQUE compuesto uq_pedido_impuesto (pedido_id, pedido_item_id, titulo) | Sí | Línea gravada; NULL = impuesto a nivel de envío/cabecera. |
| titulo | VARCHAR(100) | UNIQUE compuesto uq_pedido_impuesto (pedido_id, pedido_item_id, titulo) | No | Nombre de la tasa aplicada. |
| tasa | NUMERIC(7,4) | CHECK (tasa >= 0) | No | Porcentaje aplicado, congelado al confirmar. |
| monto | NUMERIC(12,2) | CHECK (monto >= 0) | No | Importe del impuesto; la suma debe igualar `pedidos.impuesto_total`. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro. |

## historial_estados_pedido

**Descripción:** Documenta cronológicamente las transiciones de estado de los pedidos y quién las registró.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del evento de historial. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE | No | Pedido cuyo estado cambió. |
| estado_anterior | VARCHAR(30) | — | Sí | Estado que tenía el pedido antes del cambio. |
| estado_nuevo | VARCHAR(30) | — | No | Estado adoptado por el pedido. |
| comentario | VARCHAR(255) | — | Sí | Explicación o contexto del cambio. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE SET NULL | Sí | Usuario que registró el cambio, si corresponde. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora del cambio de estado. |

## pasarelas_pago

**Descripción:** Define los proveedores de procesamiento de pagos disponibles para la tienda.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la pasarela. |
| nombre | VARCHAR(100) | — | No | Nombre comercial del proveedor de pago. |
| codigo | VARCHAR(50) | UNIQUE | No | Código único para identificar técnicamente la pasarela. |
| descripcion | VARCHAR(255) | — | Sí | Información sobre la pasarela o su uso. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la pasarela está habilitada. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro. |

## pagos

**Descripción:** Registra los pagos o intentos de pago de un pedido, su pasarela, importe y resultado.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del pago o intento de pago. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE | No | Pedido que se pretende cobrar. |
| pasarela_pago_id | BIGINT | FK a pasarelas_pago(id) ON DELETE RESTRICT; UNIQUE compuesto uq_pago_pasarela_transaccion (pasarela_pago_id, transaccion_externa_id) | No | Pasarela utilizada; no puede eliminarse mientras tenga pagos asociados. |
| estado | VARCHAR(30) | DEFAULT 'pendiente'; chk_pago_estado CHECK (estado IN ('pendiente', 'procesando', 'aprobado', 'rechazado', 'cancelado', 'reembolsado')) | No | Resultado o fase actual del procesamiento. |
| monto | NUMERIC(12,2) | CHECK (monto >= 0) | No | Importe solicitado o procesado. |
| moneda | CHAR(3) | DEFAULT 'PEN' | No | Moneda del pago. |
| transaccion_externa_id | VARCHAR(255) | UNIQUE compuesto uq_pago_pasarela_transaccion (pasarela_pago_id, transaccion_externa_id) | Sí | Identificador de la transacción entregado por la pasarela. Único por pasarela (V3). |
| metodo | VARCHAR(50) | — | Sí | Medio de pago utilizado, como tarjeta u otro método. |
| pagado_en | TIMESTAMPTZ | — | Sí | Momento en que se confirmó el pago. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación del intento. |

## pago_eventos

**Descripción:** Conserva los eventos recibidos o generados durante el procesamiento de cada pago.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del evento de pago. |
| pago_id | BIGINT | FK a pagos(id) ON DELETE CASCADE; UNIQUE compuesto uq_pago_evento_referencia (pago_id, referencia_externa) | No | Pago al que corresponde el evento. |
| tipo_evento | VARCHAR(50) | — | No | Clase de notificación o suceso procesado. |
| estado_anterior | VARCHAR(30) | — | Sí | Estado del pago antes del evento. |
| estado_nuevo | VARCHAR(30) | — | Sí | Estado del pago después del evento. |
| payload | JSONB | — | Sí | Contenido estructurado recibido o almacenado con el evento. |
| referencia_externa | VARCHAR(255) | UNIQUE compuesto uq_pago_evento_referencia (pago_id, referencia_externa) | Sí | Referencia proporcionada por el sistema externo. Debe ser el event-id del proveedor; deduplica webhooks (V3). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro del evento. |

## reembolsos

**Descripción:** Administra las devoluciones de dinero vinculadas tanto con el pedido como con el pago original.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del reembolso. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE | No | Pedido cuyo importe se devuelve total o parcialmente. |
| pago_id | BIGINT | FK a pagos(id) ON DELETE CASCADE; UNIQUE compuesto uq_reembolso_pago_transaccion (pago_id, transaccion_externa_id) | No | Pago sobre el que se ejecuta el reembolso. |
| monto | NUMERIC(12,2) | CHECK (monto > 0) | No | Importe a devolver, necesariamente positivo. |
| moneda | CHAR(3) | DEFAULT 'PEN' | No | Moneda del reembolso. |
| motivo | VARCHAR(255) | — | Sí | Razón de la devolución. |
| estado | VARCHAR(30) | DEFAULT 'pendiente'; chk_reembolso_estado CHECK (estado IN ('pendiente', 'procesando', 'aprobado', 'rechazado')) | No | Situación actual del proceso de reembolso. |
| transaccion_externa_id | VARCHAR(255) | UNIQUE compuesto uq_reembolso_pago_transaccion (pago_id, transaccion_externa_id) | Sí | Identificador de la devolución en la pasarela externa. Único por pago (V3). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de solicitud o registro. |

## transportistas

**Descripción:** Mantiene el catálogo de empresas logísticas y sus recursos generales de seguimiento.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del transportista. |
| nombre | VARCHAR(100) | UNIQUE | No | Nombre único de la empresa transportista. |
| codigo | VARCHAR(50) | UNIQUE | No | Código único para integración o identificación interna. |
| url_tracking | VARCHAR(500) | — | Sí | URL o plantilla general para consultar seguimientos. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si el transportista puede utilizarse. |

## envios

**Descripción:** Registra los despachos de un pedido, su origen, transportista, seguimiento y fechas logísticas.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del envío. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE | No | Pedido que se está despachando. |
| transportista_id | BIGINT | FK a transportistas(id) ON DELETE SET NULL | Sí | Transportista seleccionado del catálogo. |
| ubicacion_origen_id | BIGINT | FK a ubicaciones(id) ON DELETE SET NULL | Sí | Ubicación desde la cual sale la mercadería. |
| numero_tracking | VARCHAR(100) | — | Sí | Número de seguimiento proporcionado para el envío. |
| url_tracking | VARCHAR(500) | — | Sí | Enlace específico para consultar este envío. |
| transportista | VARCHAR(100) | — | Sí | Nombre del transportista conservado directamente en el envío. |
| estado | VARCHAR(30) | DEFAULT 'pendiente'; chk_envio_estado CHECK (estado IN ('pendiente', 'preparando', 'enviado', 'en_transito', 'en_reparto', 'entregado', 'devuelto', 'cancelado')) | No | Etapa actual del proceso logístico. |
| enviado_en | TIMESTAMPTZ | — | Sí | Momento en que el envío fue despachado. |
| entregado_en | TIMESTAMPTZ | — | Sí | Momento en que se confirmó la entrega. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación del envío. |

## envio_items

**Descripción:** Especifica qué cantidades de las líneas del pedido se incluyen en cada envío.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la línea de envío. |
| envio_id | BIGINT | FK a envios(id) ON DELETE CASCADE; UNIQUE compuesto uq_envio_pedido_item (envio_id, pedido_item_id) | No | Envío en el que se incluye la mercadería. |
| pedido_item_id | BIGINT | FK a pedido_items(id) ON DELETE CASCADE; UNIQUE compuesto uq_envio_pedido_item (envio_id, pedido_item_id) | No | Línea del pedido que se despacha. Una vez por envío; parciales entre envíos sí (V6). |
| cantidad | INTEGER | CHECK (cantidad > 0) | No | Número de unidades incluidas en este envío. |

## envio_eventos

**Descripción:** Almacena el historial de seguimiento proporcionado para un envío, con ubicación y fechas del suceso.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del evento de seguimiento. |
| envio_id | BIGINT | FK a envios(id) ON DELETE CASCADE | No | Envío al que pertenece el suceso. |
| estado | VARCHAR(50) | — | No | Estado logístico comunicado en el evento. |
| descripcion | VARCHAR(255) | — | Sí | Detalle legible del suceso de seguimiento. |
| ubicacion | VARCHAR(255) | — | Sí | Lugar en el que ocurrió el evento. |
| ocurrido_en | TIMESTAMPTZ | — | No | Momento real en que sucedió el evento. |
| referencia_externa | VARCHAR(255) | — | Sí | Identificador del evento en el sistema del transportista. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de registro del evento en la tienda. |

## descuentos

**Descripción:** Configura promociones mediante código, tipo de beneficio, valor, límites de uso y periodo de vigencia.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del descuento. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE RESTRICT | No | Tienda propietaria (V11). |
| codigo | VARCHAR(50) | UNIQUE | No | Código único que permite aplicar la promoción. |
| tipo | VARCHAR(30) | chk_tipo_descuento CHECK (tipo IN ('porcentaje', 'monto_fijo', 'envio_gratis')) | No | Modalidad del beneficio concedido. |
| valor | NUMERIC(12,2) | CHECK (valor >= 0) | No | Magnitud del porcentaje o monto fijo, según el tipo. |
| descripcion | VARCHAR(255) | — | Sí | Explicación comercial de la promoción. |
| uso_maximo | INTEGER | CHECK (uso_maximo > 0) | Sí | Límite total de aplicaciones permitido, si se establece. |
| usos_actuales | INTEGER | DEFAULT 0; CHECK (usos_actuales >= 0) | No | Número de aplicaciones acumuladas. |
| inicia_en | TIMESTAMPTZ | — | Sí | Inicio opcional del periodo de vigencia. |
| termina_en | TIMESTAMPTZ | — | Sí | Fin opcional del periodo de vigencia. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si el descuento está habilitado. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |

## pedido_descuentos

**Descripción:** Registra los descuentos efectivamente aplicados a cada pedido y el importe concedido.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la aplicación del descuento. |
| pedido_id | BIGINT | FK a pedidos(id) ON DELETE CASCADE; UNIQUE compuesto uq_pedido_descuento (pedido_id, descuento_id) | No | Pedido beneficiado por el descuento. |
| descuento_id | BIGINT | FK a descuentos(id) ON DELETE RESTRICT; UNIQUE compuesto uq_pedido_descuento (pedido_id, descuento_id) | No | Descuento aplicado; no puede eliminarse mientras esté usado por pedidos. Un código, una vez por pedido (V6). |
| monto_aplicado | NUMERIC(12,2) | CHECK (monto_aplicado >= 0) | No | Importe del beneficio registrado en el pedido. |

## descuento_productos

**Descripción:** Resume los productos a los que se limita un descuento y evita asociaciones repetidas.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asociación. |
| descuento_id | BIGINT | FK a descuentos(id) ON DELETE CASCADE; UNIQUE compuesto uq_descuento_producto (descuento_id, producto_id) | No | Descuento cuyo alcance se define. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE; UNIQUE compuesto uq_descuento_producto (descuento_id, producto_id) | No | Producto comprendido por el descuento. |

## descuento_colecciones

**Descripción:** Resume las colecciones comprendidas por un descuento y evita asociaciones repetidas.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asociación. |
| descuento_id | BIGINT | FK a descuentos(id) ON DELETE CASCADE; UNIQUE compuesto uq_descuento_coleccion (descuento_id, coleccion_id) | No | Descuento cuyo alcance se define. |
| coleccion_id | BIGINT | FK a colecciones(id) ON DELETE CASCADE; UNIQUE compuesto uq_descuento_coleccion (descuento_id, coleccion_id) | No | Colección comprendida por el descuento. |

## resenas

**Descripción:** Almacena las valoraciones y comentarios de clientes sobre productos, con control de aprobación editorial.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la reseña. |
| producto_id | BIGINT | FK a productos(id) ON DELETE SET NULL; UNIQUE compuesto uq_resena_producto_cliente (producto_id, cliente_id) | Sí | Producto valorado. Se conserva la reseña si el producto se elimina (V4). Una por cliente (V6). |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE SET NULL; UNIQUE compuesto uq_resena_producto_cliente (producto_id, cliente_id) | Sí | Cliente autor de la reseña, si se conserva la asociación. |
| calificacion | INTEGER | CHECK (calificacion BETWEEN 1 AND 5) | No | Puntuación otorgada en una escala de uno a cinco. |
| titulo | VARCHAR(255) | — | Sí | Encabezado breve de la reseña. |
| comentario | TEXT | — | Sí | Opinión detallada del cliente. |
| aprobada | BOOLEAN | DEFAULT FALSE | No | Indica si la reseña fue aprobada para publicación. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación de la reseña. |

## listas_deseos

**Descripción:** Define las listas personales en las que los clientes guardan productos de interés.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la lista de deseos. |
| cliente_id | BIGINT | FK a clientes(id) ON DELETE CASCADE | No | Cliente propietario de la lista. |
| nombre | VARCHAR(100) | DEFAULT 'Favoritos' | No | Nombre asignado a la lista. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación de la lista. |

## lista_deseo_items

**Descripción:** Contiene los productos guardados en cada lista de deseos y evita duplicarlos dentro de la misma lista.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del elemento de la lista. |
| lista_deseo_id | BIGINT | FK a listas_deseos(id) ON DELETE CASCADE; UNIQUE compuesto uq_lista_deseo_producto (lista_deseo_id, producto_id) | No | Lista en la que se guarda el producto. |
| producto_id | BIGINT | FK a productos(id) ON DELETE CASCADE; UNIQUE compuesto uq_lista_deseo_producto (lista_deseo_id, producto_id) | No | Producto añadido a la lista. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora en que se añadió el producto. |

## tiendas

**Descripción:** Tenants de la plataforma multi-tienda. Tabla añadida en la migración V10.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la tienda. |
| nombre | VARCHAR(255) | — | No | Nombre comercial de la tienda. |
| slug | VARCHAR(100) | UNIQUE | No | Identificador textual único. |
| descripcion | VARCHAR(255) | — | Sí | Descripción de la tienda. |
| activa | BOOLEAN | DEFAULT TRUE | No | La baja es desactivar, nunca borrar con recursos (RESTRICT en V11). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización (trigger V7). |

## tienda_pasarelas

**Descripción:** Credenciales de pasarela por tienda. Tabla añadida en la migración V10 (RF-PAY-01).

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la configuración. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE CASCADE; UNIQUE compuesto uq_tienda_pasarela (tienda_id, pasarela_pago_id) | No | Tienda dueña de las credenciales. |
| pasarela_pago_id | BIGINT | FK a pasarelas_pago(id) ON DELETE RESTRICT; UNIQUE compuesto uq_tienda_pasarela (tienda_id, pasarela_pago_id) | No | Pasarela configurada. |
| clave_publica | VARCHAR(255) | — | No | Clave pública de la tienda en esa pasarela. |
| secreto_cifrado | TEXT | — | No | Secreto cifrado (llave en entorno), jamás en claro. |
| modo | VARCHAR(10) | DEFAULT 'test'; CHECK (modo IN ('test', 'vivo')) | No | Entorno de las credenciales. |
| activo | BOOLEAN | DEFAULT TRUE | No | Indica si la configuración está habilitada. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la última actualización (trigger V7). |

## tienda_usuarios

**Descripción:** Rol de un usuario dentro de una tienda. Tabla añadida en la migración V12.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la asignación. |
| tienda_id | BIGINT | FK a tiendas(id) ON DELETE CASCADE; UNIQUE compuesto uq_tienda_usuario (tienda_id, usuario_id) | No | Tienda del rol. |
| usuario_id | BIGINT | FK a usuarios(id) ON DELETE CASCADE; UNIQUE compuesto uq_tienda_usuario (tienda_id, usuario_id) | No | Usuario con rol en la tienda. |
| rol_tienda | VARCHAR(20) | CHECK (rol_tienda IN ('admin', 'operador')) | No | Rol dentro de la tienda; `super_admin` va en `usuario_roles` global. |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de la asignación. |

## suscripciones

**Descripción:** Suscripción de la tienda al software (una por tienda). Tabla añadida en la migración V18.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador de la suscripción. |
| tienda_id | BIGINT | UNIQUE; FK a tiendas(id) ON DELETE RESTRICT | No | Tienda suscrita; la baja es desactivar. |
| plan | VARCHAR(20) | CHECK (mensual, anual) | No | Ciclo de facturación. |
| estado | VARCHAR(20) | DEFAULT 'pendiente'; CHECK (pendiente, activa, vencida, cancelada) | No | Máquina de estados (V18). |
| stripe_customer_id | VARCHAR(255) | — | Sí | Customer de Stripe (V19). |
| stripe_subscription_id | VARCHAR(255) | UNIQUE | Sí | Subscription de Stripe; varios NULL conviven (V19). |
| periodo_fin | TIMESTAMPTZ | — | Sí | Fin del periodo pagado; lo fija el webhook (+30/+365 días). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de creación. |
| actualizado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Última actualización (trigger V7). |

## suscripcion_eventos

**Descripción:** Eventos de facturación procesados, con deduplicación. Tabla añadida en la migración V18.

| Campo | Tipo | Restricciones | Nulo | Descripción |
|---|---|---|---|---|
| id | BIGSERIAL | PK | No | Identificador del evento. |
| suscripcion_id | BIGINT | FK a suscripciones(id) ON DELETE CASCADE | No | Suscripción afectada. |
| referencia_externa | VARCHAR(255) | UNIQUE | No | Event-id externo (dedup). |
| tipo | VARCHAR(100) | — | No | Tipo de evento (suscripcion.pagada, suscripcion.vencida). |
| creado_en | TIMESTAMPTZ | DEFAULT NOW() | No | Fecha y hora de proceso. |
