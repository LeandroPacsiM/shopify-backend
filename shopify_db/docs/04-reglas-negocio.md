# Reglas de negocio

## Alcance y criterio de implementación

Estas reglas se derivan de `database/01_esquema_shopify.sql`. Las restricciones de clave primaria, clave foránea, `UNIQUE` y `CHECK` indicadas como tales sí existen en PostgreSQL. Las comprobaciones de pertenencia entre entidades, cálculos de totales, transiciones, concurrencia e integración con servicios externos deben implementarse en Spring Boot; no se atribuyen a la base de datos salvo que el esquema las declare expresamente.

### Regla: Un producto se compra mediante variantes

**Descripción**: `productos` contiene la información común y `producto_variantes` representa cada versión comprable, con su precio, SKU, código de barras, peso y disponibilidad lógica. El carrito y las líneas de pedido referencian variantes, no directamente productos.

**Implementación**: La base exige `producto_variantes.producto_id`, `precio >= 0` y, cuando se informan, `precio_comparativo >= 0` y `peso_gramos >= 0`; `sku` es único si no es nulo. El backend solo debe ofrecer variantes con `activo = TRUE` cuyo producto tenga `estado = 'activo'`. Los únicos estados de producto admitidos por el `CHECK` son `borrador`, `activo` y `archivado`.

**Ejemplo**: Una camiseta activa puede tener una variante por talla, cada una con su propio SKU y precio. Una variante archivada lógicamente mediante `activo = FALSE` deja de ser ofrecida, aunque sus pedidos históricos se conserven.

### Regla: La combinación de opciones debe ser completa, coherente y no ambigua

**Descripción**: Las opciones se definen en `producto_opciones`, sus valores en `producto_opcion_valores` y la composición de cada variante en `variante_opcion_valores`. Una variante debe combinar únicamente valores de opciones pertenecientes a su mismo producto y no debe repetir una opción con valores distintos.

**Implementación**: El `UNIQUE (variante_id, opcion_valor_id)` evita repetir exactamente el mismo valor en una variante, y las claves foráneas garantizan que los registros referenciados existan. La base **no** comprueba que el valor pertenezca al producto de la variante, que haya un valor por cada opción ni que dos variantes no tengan la misma combinación completa. Spring Boot debe validar esas condiciones en una transacción y controlar altas concurrentes; no existe un constraint que impida por sí solo combinaciones ambiguas.

**Ejemplo**: Para una camiseta con las opciones Color y Talla, la variante `CAM-ROJA-M` debe asociarse con Rojo y M de esa camiseta. El backend debe rechazar Rojo de otro producto, dos colores en la misma variante o una segunda variante con exactamente Rojo + M.

### Regla: El inventario se controla por artículo y ubicación

**Descripción**: Cada `articulos_inventario` se vincula con una variante y cada fila de `niveles_inventario` expresa el stock de ese artículo en una `ubicaciones`. Por tanto, una variante puede tener existencias diferenciadas por almacén.

**Implementación**: `UNIQUE (articulo_inventario_id, ubicacion_id)` impide duplicar el nivel para el mismo par. `disponible` y `reservado` deben ser mayores o iguales que cero. `controla_stock` y `requiere_envio` guían el comportamiento de venta y logística. La base no impone una sola ficha de artículo por variante porque `articulos_inventario.variante_id` no es único; si el negocio adopta esa correspondencia, debe garantizarla la aplicación.

**Ejemplo**: El artículo de la variante `CAM-ROJA-M` puede tener 8 unidades disponibles en Lima y 3 en Arequipa, en dos niveles distintos. Agregar otra fila para el mismo artículo y Lima sería rechazado por el `UNIQUE`.

### Regla: Reservas y movimientos de inventario son operaciones atómicas de aplicación

**Descripción**: `movimientos_inventario` documenta entradas, salidas, ajustes, reservas y liberaciones relacionadas con un nivel. El movimiento y la actualización de `disponible` o `reservado` deben reflejar una misma operación de negocio.

**Implementación**: El `CHECK` de `tipo_movimiento` solo permite `entrada`, `salida`, `ajuste`, `reserva` y `liberacion`, y `cantidad` debe ser distinta de cero. No hay triggers que actualicen niveles ni constraints que comparen `reservado` con `disponible`. Spring Boot debe bloquear o actualizar condicionalmente la fila del nivel, validar stock suficiente cuando `controla_stock = TRUE`, escribir el movimiento y confirmar ambos cambios en una transacción. La referencia al pedido u otra operación puede registrarse en `referencia`, sin crear tablas adicionales.

**Ejemplo**: Al reservar dos unidades, el backend bloquea el nivel del artículo en Lima, verifica la disponibilidad, actualiza los contadores según su política y agrega un movimiento `reserva` con cantidad 2. Si cualquier paso falla, revierte ambos cambios.

### Regla: Un carrito pertenece a un CUSTOMER o a un visitante identificado por token

**Descripción**: `carritos.cliente_id` admite nulo para visitantes. Todo carrito, autenticado o anónimo, posee un `token` obligatorio y único. Al eliminar el CUSTOMER, su `cliente_id` pasa a nulo y el carrito no se borra.

**Implementación**: La unicidad de `token` y el comportamiento `ON DELETE SET NULL` son reales. El backend debe emitir tokens opacos, comprobar que el token presentado corresponde al carrito y, cuando exista `cliente_id`, verificar también su titularidad. Los estados permitidos son `activo`, `convertido`, `abandonado` y `expirado`; la validez temporal respecto de `expira_en` y la decisión de tener un único carrito activo por actor se controlan en aplicación.

**Ejemplo**: Un visitante recibe un carrito con `cliente_id = NULL` y un token aleatorio. Tras autenticarse, el backend puede asociar ese mismo carrito a su perfil CUSTOMER, conservando el token y las líneas.

### Regla: Cada variante aparece una sola vez por carrito

**Descripción**: `carrito_items` guarda cantidad y precio unitario de una variante dentro de un carrito. Volver a agregar la misma variante debe actualizar la cantidad de la línea existente en lugar de crear un duplicado lógico.

**Implementación**: El constraint `uq_carrito_variante` impone `UNIQUE (carrito_id, variante_id)`; además, `cantidad > 0` y `precio_unitario >= 0`. El backend debe validar nuevamente producto, variante y stock, resolver de forma atómica el conflicto de inserción y recalcular el precio cuando corresponda. El precio almacenado en el carrito no garantiza el precio final del pedido.

**Ejemplo**: Si el carrito ya tiene dos unidades de una variante y se agrega una más, la línea queda con cantidad tres. El checkout vuelve a consultar el precio vigente antes de calcular sus totales.

### Regla: El checkout congela una propuesta de totales, no una verdad calculada por la base

**Descripción**: `checkout_sesiones` vincula un carrito con CUSTOMER, direcciones y método de envío, y registra subtotal, impuestos, envío, descuentos y total. Sirve como contexto de confirmación y expiración de la compra.

**Implementación**: Los cinco importes tienen `CHECK` de no negatividad y el estado solo puede ser `pendiente`, `completado`, `expirado` o `cancelado`. Las claves foráneas no demuestran que las direcciones pertenezcan al CUSTOMER ni que los totales sean aritméticamente correctos. El backend debe comprobar titularidad, carrito activo, vigencia, método y tasa aplicables, precios, descuentos y stock, y calcular los importes con redondeo monetario uniforme. Puede haber varias sesiones para un carrito porque no hay unicidad sobre `carrito_id`; la aplicación decide cuál está vigente.

**Ejemplo**: Para un subtotal de 200, envío de 15 y descuento de 20, el backend calcula el impuesto según la tasa seleccionada y persiste todos los importes. La base acepta que sean no negativos, pero no recalcula `total` ni verifica la fórmula.

### Regla: La creación del pedido y sus líneas es una unidad transaccional

**Descripción**: Un pedido nace de una sesión de checkout válida y contiene una o más líneas en `pedido_items`, junto con sus direcciones históricas en `pedido_direcciones`. El número de pedido identifica de manera única la compra.

**Implementación**: `pedidos.checkout_sesion_id` es único, por lo que una sesión puede quedar vinculada como máximo a un pedido; `numero_pedido` también es único. `pedido_items.pedido_id`, `cantidad > 0` y precios no negativos sí están garantizados. La aplicación debe volver a leer carrito y checkout con bloqueo, impedir una conversión duplicada, validar totales y disponibilidad, copiar líneas y direcciones, crear el pedido `pendiente`, y actualizar checkout y carrito en la misma transacción local. No existe un constraint que exija que la suma de líneas coincida con `subtotal` ni que `precio_total = cantidad * precio_unitario`.

**Ejemplo**: Al confirmar, Spring Boot crea el pedido, sus dos líneas y sus dos direcciones, marca el checkout `completado` y el carrito `convertido`. Si falla una línea, no queda un pedido parcial.

### Regla: Las líneas del pedido conservan un snapshot histórico

**Descripción**: `pedido_items` guarda `producto_titulo`, `variante_titulo`, `sku`, cantidad y precios del momento de la compra. La referencia `variante_id` es opcional para permitir conservar la línea si la variante desaparece.

**Implementación**: La acción `ON DELETE SET NULL` de `variante_id` es real; los textos y precios no se rellenan mediante triggers. El backend debe copiarlos desde el producto, la variante y el cálculo confirmado al crear el pedido, y los comprobantes deben usar este snapshot en vez de consultar valores actuales del catálogo. La base no valida que el título o SKU copiado coincidan con los originales.

**Ejemplo**: Si una variante se vendió como “Camiseta roja / M”, SKU `CAM-ROJA-M`, a 50, un cambio posterior de título o precio no altera la línea histórica. Si se elimina la variante, la línea mantiene esos datos y su `variante_id` queda nulo.

### Regla: Los cambios de estado del pedido deben quedar historizados

**Descripción**: El estado actual reside en `pedidos.estado` y los cambios se registran en `historial_estados_pedido` con estado anterior, nuevo, comentario, usuario y fecha.

**Implementación**: El `CHECK` de `pedidos.estado` admite únicamente `pendiente`, `pagado`, `en_preparacion`, `enviado`, `entregado`, `cancelado` y `reembolsado`. Las columnas de estado del historial no tienen ese `CHECK`; por ello, el backend debe usar el mismo vocabulario, validar la transición y escribir pedido e historial en una sola transacción. La base no impide secuencias inválidas ni inserta el historial automáticamente.

**Ejemplo**: Al confirmar el pago total, el backend cambia `pendiente` a `pagado` y agrega un historial con esos dos valores. No debe pasar un pedido entregado a `pagado` por una notificación tardía duplicada.

### Regla: Un pedido admite múltiples pagos o intentos de pago

**Descripción**: La relación `pedidos`–`pagos` es de uno a muchos: pueden coexistir intentos rechazados, un pago aprobado o varios aportes que cubran el total, según la política comercial.

**Implementación**: `pagos.pedido_id` es una clave foránea sin unicidad, por lo que la cardinalidad múltiple es real. Cada pago tiene monto y moneda propios y estados `pendiente`, `procesando`, `aprobado`, `rechazado`, `cancelado` o `reembolsado`. La base no limita la suma de pagos al total del pedido ni exige monedas iguales. Spring Boot debe validar importe, moneda, saldo y concurrencia antes de aceptar nuevos intentos o marcar el pedido `pagado`.

**Ejemplo**: Un primer intento de 100 queda `rechazado` y un segundo por el mismo importe queda `aprobado`; ambos registros pertenecen al mismo pedido. El backend considera solo los aprobados al calcular el saldo.

### Regla: Una pasarela puede procesar muchos pagos y no se elimina si tiene historial

**Descripción**: `pasarelas_pago` identifica al proveedor externo y cada pago exige exactamente una pasarela. Una pasarela se relaciona con muchos pagos a lo largo del tiempo.

**Implementación**: `pasarelas_pago.codigo` es único y `pagos.pasarela_pago_id` usa `ON DELETE RESTRICT`, que impide borrar una pasarela referenciada. La desactivación se representa con `activo = FALSE`. El backend selecciona únicamente pasarelas activas para nuevas operaciones, pero conserva la referencia de las históricas.

**Ejemplo**: Todos los cargos realizados con una pasarela registrada apuntan a su mismo identificador. Para retirarla de nuevas compras se desactiva; no se elimina si ya tiene pagos.

### Regla: Los eventos de pago sustentan la conciliación, pero no son una bandeja automática de webhooks

**Descripción**: `pago_eventos` conserva tipo de evento, estados anterior y nuevo, payload y referencia externa de las notificaciones o cambios asociados a un pago.

**Implementación**: La base relaciona cada evento con un pago mediante `ON DELETE CASCADE` y el `UNIQUE (pago_id, referencia_externa)` de V3 deduplica webhooks repetidos (la referencia debe ser el event-id del proveedor). Spring Boot debe autenticar la notificación, localizar el pago correcto, validar la transición, registrar el evento y actualizar el pago en una transacción. Al copiar estados de pago se emplean exclusivamente los valores permitidos por `pagos`. No se presupone una tabla de inbox.

**Ejemplo**: Dos notificaciones idénticas de aprobación no deben generar dos transiciones funcionales. El backend reconoce la referencia ya procesada según su mecanismo de control y evita volver a marcar o historizar el pedido.

### Regla: Un pedido puede dividirse en varios envíos

**Descripción**: `envios` tiene una clave foránea no única a `pedidos`, lo que permite separar una compra por ubicación de origen, transportista o disponibilidad de sus artículos.

**Implementación**: La cardinalidad uno a muchos es real. Cada envío tiene sus propios datos de transportista, origen, tracking y fechas. Su `CHECK` solo permite `pendiente`, `preparando`, `enviado`, `en_transito`, `en_reparto`, `entregado`, `devuelto` y `cancelado`. El backend decide la planificación, evita crear envíos vacíos y determina cuándo el conjunto justifica cambiar el estado global del pedido; la base no calcula ese estado agregado.

**Ejemplo**: Un pedido con tres unidades puede generar un envío desde Lima y otro desde Arequipa, cada uno con su propio número de tracking. El pedido solo se marca `entregado` cuando la aplicación confirma la entrega completa.

### Regla: Las cantidades de envío deben reconciliarse con las líneas del pedido

**Descripción**: `envio_items` indica cuántas unidades de una `pedido_items` viajan en cada envío. Las cantidades parciales permiten dividir una misma línea entre envíos distintos.

**Implementación**: El `CHECK cantidad > 0` y las claves foráneas existen, y el `UNIQUE (envio_id, pedido_item_id)` de V6 impide duplicar una línea dentro del mismo envío. Sigue sin haber una clave que garantice que envío y línea pertenezcan al mismo pedido ni un constraint que compare cantidades. Spring Boot debe validar esa pertenencia y comprobar que cada envío y la suma de todos los envíos no excedan lo pedido. Estas validaciones requieren bloqueo o control de concurrencia.

**Ejemplo**: Si una línea contiene cinco unidades, un envío puede llevar dos y otro tres. El backend rechaza un tercer envío con una unidad adicional y también una línea perteneciente a otro pedido.

### Regla: El tracking conserva eventos fechados y separa el dato externo del estado local

**Descripción**: `envio_eventos` almacena el estado comunicado por el transportista, descripción, ubicación, fecha de ocurrencia y referencia externa. `envios` mantiene el estado logístico normalizado y los datos actuales de seguimiento.

**Implementación**: `envio_eventos.estado` es texto sin `CHECK`, mientras `envios.estado` sí tiene el conjunto cerrado indicado en la regla anterior. El backend debe validar la procedencia, ordenar o descartar eventos obsoletos según `ocurrido_en`, controlar duplicados y mapear el valor externo a un estado local permitido. No existe unicidad sobre `referencia_externa` ni actualización automática del envío.

**Ejemplo**: Un evento externo de salida del centro logístico se guarda con su texto original y fecha; si la regla de mapeo corresponde, el envío cambia a `en_transito`. Un código desconocido queda registrado sin introducir un valor no permitido en `envios.estado`.

### Regla: Un pedido puede tener varios descuentos aplicados

**Descripción**: `pedido_descuentos` relaciona cada pedido con los descuentos efectivamente aplicados y conserva el monto otorgado por cada uno. La relación no única con `pedidos` permite el multidescuento. Desde V19, `checkout_descuentos` conserva los mismos códigos y montos calculados durante el checkout; al confirmar se trasladan a `pedido_descuentos`.

**Implementación**: `monto_aplicado >= 0` y las claves foráneas son reales; `descuento_id` usa `ON DELETE RESTRICT` para conservar el historial. El `UNIQUE (pedido_id, descuento_id)` de V6 y el `UNIQUE (checkout_sesion_id, descuento_id)` de V19 impiden aplicar el mismo código dos veces al pedido o al checkout (como Shopify). El backend debe validar compatibilidad y acumulación entre códigos distintos (máx. 5 producto/pedido + 1 envío; shipping+shipping nunca), calcular cada importe y hacer coincidir la suma con el total mediante su lógica transaccional.

**Ejemplo**: Un pedido puede recibir un descuento porcentual y otro de envío gratis si la política permite combinarlos. Cada aplicación genera su fila con el importe calculado; una notificación repetida no debe duplicarlas.

### Regla: La vigencia, el alcance y el uso de un descuento se validan al aplicarlo

**Descripción**: `descuentos` define tipo, valor, vigencia, estado activo y límite de uso. `descuento_productos` y `descuento_colecciones` delimitan opcionalmente los productos o colecciones a los que se aplica.

**Implementación**: Los tipos permitidos son `porcentaje`, `monto_fijo` y `envio_gratis`; `valor >= 0`, `uso_maximo > 0` cuando se informa y `usos_actuales >= 0`. Las restricciones únicas evitan repetir un producto o colección dentro del mismo descuento. La base no limita un porcentaje a 100, no compara usos actuales con el máximo y no evalúa fechas ni alcance. Spring Boot debe hacerlo, recalcular el total y consumir el uso de forma atómica al confirmar el pedido, definiendo también cómo revierte el uso ante cancelación o reembolso.

**Ejemplo**: Antes de aceptar un código, el backend comprueba `activo`, `inicia_en`, `termina_en`, disponibilidad de usos y elegibilidad de las líneas. Dos confirmaciones concurrentes no deben exceder un uso máximo restante de uno.

### Regla: Los reembolsos deben corresponder al mismo pedido y pago

**Descripción**: `reembolsos` registra el importe, motivo, moneda y estado de una devolución asociada simultáneamente a un pedido y a un pago.

**Implementación**: Los estados permitidos son `pendiente`, `procesando`, `aprobado` y `rechazado`, y `monto > 0`. Las dos claves foráneas no forman una restricción compuesta: la base no comprueba que `pago_id` pertenezca a `pedido_id`, ni limita la suma de reembolsos. El backend debe verificar ambas relaciones, el saldo reembolsable y la moneda, invocar la pasarela y actualizar estados de forma consistente. Los estados `reembolsado` de pago y pedido se reservan para la condición completa definida por el negocio, porque no existe un estado parcial.

**Ejemplo**: Para devolver 30 de un pago aprobado de 100, se valida que el pago sea del pedido indicado y que 30 no exceda el saldo. Un reembolso parcial aprobado queda representado por su registro y monto, sin inventar estados adicionales.

### Regla: Las reseñas tienen una calificación cerrada y publicación moderada

**Descripción**: `resenas` vincula una opinión con un producto y opcionalmente con un CUSTOMER. La calificación es un entero de 1 a 5 y `aprobada` controla su publicación.

**Implementación**: El `CHECK calificacion BETWEEN 1 AND 5` es real. Si se elimina el CUSTOMER, `cliente_id` queda nulo; si se elimina el producto, la reseña se conserva con `producto_id` nulo (V4, como Shopify). El `UNIQUE (producto_id, cliente_id)` de V6 impide reseñas repetidas del mismo CUSTOMER al mismo producto. El backend debe validar la autoría, aplicar la política de compra verificada si se adopta y mostrar públicamente solo reseñas aprobadas.

**Ejemplo**: Una calificación de 5 puede quedar inicialmente no aprobada hasta la moderación. Una calificación de 0 o 6 es rechazada por PostgreSQL.

### Regla: Los metacampos son extensiones tipadas por convención de aplicación

**Descripción**: `metacampos` permite agregar pares clave-valor a distintas entidades mediante `entidad`, `entidad_id`, `clave`, `valor` y `tipo`, sin modificar el esquema para cada atributo adicional.

**Implementación**: `UNIQUE (entidad, entidad_id, clave)` evita repetir una clave dentro del mismo objeto. No existe clave foránea polimórfica desde `entidad_id` ni un `CHECK` de nombres de entidad o tipos. El backend debe mantener un catálogo controlado de entidades y tipos, verificar que el destino exista, convertir y validar `valor`, autorizar la modificación y limpiar metacampos huérfanos cuando corresponda.

**Ejemplo**: Un metacampo con entidad `productos`, el identificador del producto y clave `material` puede guardar un texto. La aplicación impide usar ese identificador si el producto no existe y evita interpretar como número un valor no válido.
