# Observaciones y riesgos detectados

## Alcance

Este documento registra riesgos, limitaciones, mejoras y trabajo pendiente detectados en `database/01_esquema_shopify.sql`. Las observaciones describen el estado actual y **no implican que las recomendaciones ya estén implementadas**, salvo la corrección del comentario de base de datos indicada a continuación.

El esquema actual define 54 tablas, 79 claves foráneas, restricciones `PRIMARY KEY`, `UNIQUE` y `CHECK`, además de índices secundarios y triggers de `actualizado_en` (V7). La base cubre un recorrido amplio de comercio electrónico; las reglas que siguen delegadas a la aplicación se indican en cada punto.

## Corrección aplicada del comentario de base de datos

La sentencia original `COMMENT ON DATABASE IS ...` no incluía el nombre de la base y era inválida en PostgreSQL. Se reemplazó por un bloque que construye el comentario con `current_database()`, de modo que se aplica al nombre real de la base sobre la que se ejecuta el script. Esto permite cargar el esquema tanto en una base local como `shopify_db_demo` como en el PostgreSQL administrado de Supabase, sin fijar un nombre que pueda no coincidir.

Esta es la única corrección estructural aplicada. Los demás riesgos de este documento siguen pendientes.

## Integridad del catálogo y de inventario

### 1. Variantes con artículos de inventario duplicados

`articulos_inventario.variante_id` es obligatorio y tiene FK hacia `producto_variantes`, pero no tiene restricción `UNIQUE`. En consecuencia, una misma variante puede tener varios artículos de inventario. Si la intención es mantener un artículo por variante, los duplicados pueden fragmentar o duplicar el stock y hacer ambigua la reserva.

**Recomendación:** confirmar la cardinalidad de negocio. Si realmente es uno a uno, recoger la unicidad como mejora y revisar antes los datos existentes. No se presume aquí que las duplicidades sean siempre incorrectas.

### 2. Combinaciones de opciones sin consistencia de producto o tienda

La restricción `uq_variante_opcion_valor` de `variante_opcion_valores` solo impide repetir la pareja `(variante_id, opcion_valor_id)`. Las dos FK se validan de forma independiente. Por ello:

- un valor de opción puede pertenecer a un producto diferente del producto de la variante;
- una variante puede recibir dos valores distintos de la misma opción, por ejemplo talla `M` y talla `L` a la vez;
- tampoco se garantiza pertenencia a una misma tienda: resuelto en V10–V11 con `tiendas` + `tienda_id` en 9 raíces y filtro obligatorio en aplicación.

**Recomendación:** validar temporalmente en la aplicación que la variante, la opción y el valor compartan producto, y que exista exactamente un valor por opción cuando la configuración lo requiera. Como evolución, evaluar una restricción estructural que haga cumplir la cardinalidad y la pertenencia, incluyendo tienda solo si ese concepto se incorpora formalmente al modelo.

### 3. Imágenes de variante sin garantía de mismo producto

`producto_imagenes` tiene FK independientes hacia `productos` y `producto_variantes`. Cuando `variante_id` no es nulo, el esquema no impide que la variante pertenezca a otro producto.

**Recomendación:** comprobar la pertenencia al producto al asignar una imagen de variante y considerar una regla estructural en una migración posterior.

### 4. Movimientos y niveles de inventario no forman una operación automática

`movimientos_inventario` registra entradas, salidas, ajustes, reservas y liberaciones, pero no existe un trigger que actualice `niveles_inventario`. Un movimiento puede quedar registrado sin modificar `disponible` o `reservado`, o los niveles pueden cambiar sin dejar movimiento. Los checks no negativos tampoco garantizan por sí solos la interpretación de stock total y stock reservado.

**Riesgo:** diferencias entre el historial y el saldo, especialmente con compras simultáneas o reintentos.

**Recomendación:** definir una única operación transaccional de inventario, con bloqueo o control de concurrencia, idempotencia por referencia y pruebas de reserva, liberación y sobreventa. Decidir explícitamente si `disponible` ya descuenta lo reservado y aplicar esa convención de manera uniforme.

## Unicidad ausente en relaciones de pedidos y envíos

### 5. Direcciones repetibles por pedido y tipo

No existe `UNIQUE (pedido_id, tipo)` en `pedido_direcciones`. El check limita `tipo` a `envio` o `facturacion`, pero admite varias direcciones del mismo tipo para un pedido.

**Riesgo:** la aplicación puede no saber cuál utilizar para entrega, facturación o impresión de documentos.

**Recomendación:** si cada pedido debe tener como máximo una dirección de cada tipo, registrar esa unicidad como mejora. **Resuelto en V6** con `UNIQUE (pedido_id, tipo)`. Si se desea conservar versiones, definir primero cuál es la versión vigente.

### 6. Líneas repetidas dentro de un envío

`envio_items` no tiene `UNIQUE (envio_id, pedido_item_id)`. Una misma línea de pedido puede repetirse dentro del mismo envío con cantidades separadas.

**Riesgo:** duplicación accidental al reintentar una inserción y sobreestimación de unidades enviadas.

**Recomendación:** consolidar por pareja envío/línea cuando esa sea la regla de negocio y hacer idempotentes los reintentos de integración. **Resuelto en V6** con `UNIQUE (envio_id, pedido_item_id)`; la suma entre envíos sigue validándose en aplicación con bloqueo.

### 7. Descuento repetido en el mismo pedido

`pedido_descuentos` no tiene `UNIQUE (pedido_id, descuento_id)`. El mismo descuento puede asociarse varias veces a un pedido.

**Riesgo:** acumulación involuntaria de beneficios o diferencias entre `descuento_total` y las filas de aplicación.

**Recomendación:** decidir si un cupón solo puede aplicarse una vez por pedido. **Resuelto en V6** con `UNIQUE (pedido_id, descuento_id)`; la matriz de combinación entre códigos distintos sigue en aplicación. Si se permiten varias aplicaciones legítimas, agregar una justificación o identificador de aplicación y validar el total acumulado.

## Referencias lógicas sin integridad física

### 8. Metacampos polimórficos

`metacampos.entidad` y `metacampos.entidad_id` identifican lógicamente una entidad, pero no tienen FK hacia la tabla correspondiente. El `UNIQUE (entidad, entidad_id, clave)` evita duplicar una clave para el mismo identificador lógico, no comprueba que esa entidad exista. También se pueden escribir nombres de entidad inconsistentes y quedar registros huérfanos después de borrar el destino.

**Recomendación:** mantener un catálogo de nombres de entidad permitidos, validar existencia en la capa de aplicación y realizar limpieza o comprobaciones periódicas de huérfanos. Una solución estructural debe evaluar el costo de dividir o rediseñar esta relación polimórfica.

### 9. Auditoría con destino lógico

En `registros_auditoria`, `usuario_id` sí tiene FK, pero `entidad` y `entidad_id` no referencian físicamente a la entidad auditada. Esto permite conservar auditoría aunque desaparezca el registro destino, lo cual puede ser intencional, pero también acepta destinos inexistentes desde el momento de inserción.

**Recomendación:** documentar la semántica de retención, normalizar los nombres de entidad y no presentar estas columnas como una FK. Para consultas, controlar los tipos y validar los JSON almacenados.

## Pagos y reembolsos

### 10. Reembolso sin garantía de pertenencia al mismo pedido

`reembolsos` contiene `pedido_id` y `pago_id`, cada uno con su propia FK. Nada impide seleccionar un pago que pertenezca a otro pedido. Tampoco se comprueba que la moneda del reembolso coincida con la del pago o del pedido.

**Riesgo:** contabilizar un reembolso en un pedido y debitarlo del pago de otro.

**Recomendación:** validar pedido y moneda en una operación transaccional; como mejora estructural, estudiar una FK compuesta basada en una clave que vincule pago y pedido sin ambigüedad.

### 11. Sin límite acumulado de reembolso

El check `monto > 0` evita importes nulos o negativos, pero no limita la suma de reembolsos aprobados o en proceso al importe efectivamente cobrado. Un pago puede recibir varios reembolsos cuya suma supere su monto. Tampoco se garantiza que la suma de pagos aprobados coincida con el total del pedido.

**Recomendación:** calcular el saldo reembolsable con concurrencia controlada, bloquear reembolsos simultáneos que excedan el saldo y reconciliar los estados locales con la pasarela. Definir qué estados descuentan saldo y cómo se revierte un reembolso rechazado.

### 12. Identificadores externos repetibles

`pagos.transaccion_externa_id` y `reembolsos.transaccion_externa_id` no tienen restricción `UNIQUE`. Dos filas locales pueden representar la misma operación externa, especialmente al repetir webhooks o solicitudes tras un tiempo de espera.

**Recomendación:** incorporar idempotencia. **Resuelto en V6** con `UNIQUE (pasarela_pago_id, transaccion_externa_id)` en pagos y `UNIQUE (pago_id, transaccion_externa_id)` en reembolsos. Para reembolsos, definir el ámbito a través del pago y su pasarela. También conviene distinguir el identificador de transacción del identificador único del evento recibido.

### 13. Eventos sin deduplicación ni transiciones formales

`pago_eventos` conserva `payload`, tipo, referencia y estados, pero no obliga `referencia_externa` a ser única ni valida que `estado_anterior` y `estado_nuevo` correspondan a transiciones permitidas. `pagos.estado` tiene un check de valores, no una máquina de estados.

**Recomendación:** guardar el identificador del evento de la pasarela, deduplicar antes de aplicar cambios, verificar firmas y procesar las transiciones en una transacción. **Deduplicación resuelta en V3** con `UNIQUE (pago_id, referencia_externa)`; la verificación de firmas y transiciones sigue en aplicación. El payload debe tratarse como dato no confiable hasta validarlo y no como prueba suficiente de pago.

## Totales, cantidades y cronología

### 14. Totales almacenados sin comprobación cruzada

Los checks de `checkout_sesiones`, `pedidos`, `pedido_items`, `pagos` y `pedido_descuentos` comprueban principalmente signos y rangos. No existe una restricción que garantice:

- `precio_total = cantidad * precio_unitario` en cada línea;
- la suma de líneas igual al subtotal del pedido;
- `total = subtotal - descuento_total + impuesto_total + envio_total`;
- la suma de `pedido_descuentos.monto_aplicado` igual a `descuento_total`;
- igualdad de importes entre checkout completado y pedido;
- moneda consistente entre pedido, pagos y reembolsos.

Almacenar snapshots y totales es útil para el historial, pero exige que el cálculo de aplicación sea correcto y transaccional.

**Recomendación:** centralizar el cálculo, redondear con reglas explícitas para `NUMERIC`, probar descuentos acumulados y validar nuevamente en servidor antes de confirmar el pedido. Los importes no deben aceptarse desde el navegador como fuente de verdad.

### 15. Cantidades enviadas sin tope ni pertenencia cruzada

`envio_items.cantidad` solo debe ser mayor que cero. El esquema no limita la suma enviada por línea a la cantidad comprada, ni garantiza que `pedido_items.pedido_id` sea igual a `envios.pedido_id`. Por tanto, se puede incluir en un envío una línea perteneciente a otro pedido o enviar más unidades de las solicitadas.

**Recomendación:** validar la pertenencia y el saldo pendiente por línea al crear o modificar un envío. Controlar también reintentos y envíos parciales concurrentes.

### 16. Fechas sin reglas de cronología

No hay checks que impidan, entre otros casos:

- `entregado_en` anterior a `enviado_en` en `envios`;
- `pagado_en` anterior a la creación del pago o presente en un pago rechazado;
- `termina_en` anterior a `inicia_en` en descuentos;
- `expira_en` anterior a `creado_en` en sesiones, carritos o checkout;
- eventos de pago, pedido o envío en un orden incompatible con sus transiciones.

`ocurrido_en` y `creado_en` permiten distinguir cuándo ocurrió un evento y cuándo se registró, pero no validan esa relación.

**Recomendación:** definir reglas temporales por estado, conservar las fechas en `TIMESTAMPTZ`, ordenar eventos con un desempate estable y no reconstruir la historia únicamente por el orden de llegada de webhooks.

## Seguridad, auditoría y mantenimiento

### 17. Tokens de sesión protegidos desde V8

`usuarios.password_hash` indica almacenamiento de contraseñas mediante hash. En V1 `sesiones.token` guardaba directamente el token; desde V8 solo se guarda `token_hash` (SHA-256 del refresh opaco) con rotación por `familia_id` y revocación. `token_legacy` se conserva únicamente para la transición y se elimina en el cutover. No usar los tokens ficticios de la documentación como credenciales.

### 18. Ausencia de triggers de auditoría y actualización

No existen triggers que escriban en `registros_auditoria` cuando cambian entidades ni que actualicen automáticamente `actualizado_en`. La columna aparece en `usuarios`, `productos`, `carritos`, `pedidos` y `niveles_inventario`, con `DEFAULT NOW()`, pero ese valor se aplica al insertar, no en cada `UPDATE`.

**Riesgo:** fechas de modificación obsoletas y auditoría incompleta si algún proceso omite escribirla.

**Recomendación:** definir qué eventos deben auditarse, quién es responsable de hacerlo y cómo se captura el actor. **Actualizado_en resuelto en V7** con triggers `BEFORE UPDATE` en las 5 tablas que traen la columna; la auditoría sigue escribiéndola el backend en la misma transacción (sin triggers, para evitar recursión). Si se usan más triggers, probar su rendimiento, evitar recursión y complementarlos con auditoría de acciones de aplicación. Los datos sensibles en `datos_anteriores` y `datos_nuevos` deben minimizarse y protegerse.

### 19. Índices redundantes con restricciones `UNIQUE`

PostgreSQL crea índices para respaldar las restricciones `UNIQUE`. El esquema agrega índices que normalmente duplican ese acceso, por ejemplo:

- `usuarios.email`: `UNIQUE` y `idx_usuarios_email`;
- `sesiones.token`: `UNIQUE` y `idx_sesiones_token`;
- `producto_variantes.sku`: `UNIQUE` y `idx_variantes_sku`;
- `clientes.usuario_id`: `UNIQUE` y `idx_clientes_usuario`.

También hay índices sobre el primer componente de claves únicas compuestas, como `idx_variante_opcion_valores_variante`, `idx_niveles_inventario_articulo`, `idx_producto_etiquetas_producto` e `idx_lista_deseo_items_lista`, que suelen quedar cubiertos por el índice del `UNIQUE` correspondiente.

**Riesgo:** escrituras y almacenamiento adicionales sin beneficio de consulta proporcional.

**Recomendación:** revisar planes reales, tamaño y uso con estadísticas de PostgreSQL antes de retirar índices. No eliminar el índice que respalda una restricción ni confundir unicidad con selección de un índice auxiliar.

## Compra como invitado y direcciones

`carritos.cliente_id` y `pedidos.cliente_id` aceptan nulos, por lo que el modelo permite comprar sin una cuenta registrada. Sin embargo, `direcciones.cliente_id` es obligatorio. Un visitante sin fila en `clientes` no puede tener direcciones persistidas en esa tabla, aunque `checkout_sesiones.direccion_envio_id` y `direccion_facturacion_id` sean opcionales.

**Riesgo:** si se habilita checkout de invitado, la aplicación debe mantener la dirección en memoria o en la solicitud hasta crear el snapshot de `pedido_direcciones`; no puede guardarla anticipadamente como dirección de un cliente inexistente. Si se exige crear un cliente antes de guardar la dirección, el flujo deja de ser completamente anónimo.

**Recomendación (histórica, ya decidida):** definir si el checkout de invitado forma parte del alcance. **Decidido: sin guest (decisión A) y aplicado en V5** — `pedidos.cliente_id` y `checkout_sesiones.cliente_id` son NOT NULL, el carrito anónimo sí se permite hasta el checkout. La dirección del pedido es un snapshot y no tiene FK hacia `direcciones`.

## Campos duplicados y datos mínimos

- **`envios.transportista`:** convive con `transportista_id` y con `transportistas.nombre`. No existe una regla que mantenga el texto sincronizado con el catálogo; la aplicación debe decidir si es un dato histórico, una copia temporal o si debe eliminarse en una migración futura.
- **`articulos_inventario.sku`:** duplica un dato que también existe en `producto_variantes.sku`, sin FK ni CHECK de coincidencia. Puede divergir si cambia el SKU de la variante.
- **`pedido_direcciones`:** solo `pedido_id` y `tipo` son obligatorios; `linea1`, ciudad y país admiten nulos. El backend debe exigir los campos mínimos de una dirección utilizable antes de insertar el snapshot.
- **`resenas` y `listas_deseos`:** no existe unicidad para una reseña por producto y cliente ni para un nombre de lista por cliente. Si esas reglas pertenecen al negocio, deben validarse en aplicación o agregarse mediante migración.

## Mejoras funcionales y riesgos generales

- **Reglas de descuento:** no se garantiza `usos_actuales <= uso_maximo`, ni la vigencia al aplicar el descuento, ni la actualización atómica del contador. Se requiere control transaccional y una decisión sobre usos cancelados o reembolsados.
- **Estados de negocio:** los checks validan vocabularios, no transiciones. Las transiciones de pedido, pago y envío necesitan reglas de aplicación o mecanismos adicionales.
- **Impuestos:** `tasas_impuesto` no está relacionada con checkout, pedidos o líneas. Desde V9 el histórico queda en `pedido_impuestos` (título, tasa y monto por pedido/línea); `tasas_impuesto` sirve solo para cotizar.
- **Borrado y snapshots:** varias relaciones usan `ON DELETE CASCADE` o `SET NULL`. Deben probarse los efectos antes de permitir borrados administrativos, especialmente en pedidos, pagos, envíos y catálogo.
- **Concurrencia:** carrito, inventario, contadores de descuento, pago y reembolso requieren pruebas de operaciones paralelas y reintentos; las constraints actuales no sustituyen esas pruebas.
- **Protección de datos:** `JSONB`, payloads y auditoría pueden terminar conteniendo información personal o secretos si se guardan respuestas completas de terceros. Aplicar minimización, cifrado, control de acceso y retención.
- **Observabilidad:** hacen falta métricas y registros de aplicación para webhooks, tiempos de respuesta, reintentos y diferencias de conciliación. Las tablas de eventos no sustituyen los logs operativos.
- **Escalabilidad:** una base única simplifica la consistencia, pero concentra carga y disponibilidad. Se deben medir consultas, copias de seguridad, restauración y conexiones antes de añadir caché o separar servicios.

## Pendientes propuestos

1. Verificar una instalación limpia del esquema corregido de principio a fin en un entorno PostgreSQL real.
2. Confirmar las cardinalidades de inventario, direcciones, líneas de envío y descuentos antes de añadir unicidad.
3. Diseñar validaciones para opciones de variantes, pertenencia entre pedido/envío/línea y entre pago/reembolso/pedido.
4. Definir fórmulas, redondeo, moneda y reconciliación de totales y reembolsos.
5. Establecer máquinas de estados, cronología e idempotencia para pedidos, pagos y envíos.
6. Proteger tokens, secretos y payloads; definir retención de auditoría.
7. Implementar y probar la actualización fiable de `actualizado_en` y la estrategia de auditoría.
8. Revisar índices con mediciones y planes de ejecución, no solo por inspección visual.
9. Preparar datasets separados para desarrollo, pruebas automatizadas y demostración; el archivo fuente actual no incluye seed.
10. Incorporar migraciones versionadas, copias de seguridad y pruebas de restauración antes de usar datos reales.

## Cambios necesarios para pasarelas de pago reales

Stripe, Culqi, Yape y PayPal son servicios externos, no bases de datos que deban replicarse dentro de este esquema. La integración real exige componentes de aplicación y controles que el DDL no proporciona:

1. **Adaptadores por proveedor:** encapsular creación de intenciones u órdenes, captura, consulta, cancelación y reembolso. La disponibilidad y el flujo concreto de Yape dependerán del proveedor o integración habilitada.
2. **Secretos fuera de la base y del repositorio:** usar un gestor de secretos o variables protegidas, rotación y credenciales separadas para sandbox y producción. `pasarelas_pago` debe conservar configuración pública mínima, no claves privadas.
3. **Webhooks autenticados:** verificar firma, tolerancia temporal y origen según cada proveedor; rechazar payloads inválidos y evitar que una notificación por sí sola eluda la lógica de autorización.
4. **Idempotencia de extremo a extremo:** usar claves de idempotencia al llamar a APIs, deduplicar eventos externos y reintentar con espera exponencial. Distinguir reintentos técnicos de nuevos intentos de cobro.
5. **Conciliación:** contrastar pagos y reembolsos locales con reportes o consultas del proveedor, incluidos importes, moneda, comisiones si se modelan y estados pendientes.
6. **Seguridad de tarjeta y datos personales:** usar campos alojados o tokenización del proveedor. No almacenar números completos, CVV ni secretos en `payload`, auditoría o logs.
7. **Transacciones locales y mensajería:** coordinar la actualización de `pagos`, `pago_eventos`, pedido e inventario. Evaluar un patrón outbox para no perder efectos posteriores cuando una llamada externa tenga éxito pero falle el proceso local.
8. **Manejo operativo:** configurar tiempos de espera, circuit breaker, alertas, trazabilidad por identificadores externos y procedimientos para pagos aprobados externamente pero pendientes localmente.
9. **Pruebas:** usar sandbox y eventos simulados firmados por el entorno de prueba, cubriendo duplicados, desorden, rechazo, expiración, reembolso parcial y total.

## Cambios necesarios para transportistas reales

`transportistas`, `envios` y `envio_eventos` almacenan la representación local; la información autoritativa se obtiene de APIs o webhooks de transportistas externos. Para integrarlos se requiere:

1. **Adaptadores y credenciales externas:** crear envíos, solicitar etiquetas cuando el servicio lo permita, consultar tracking y cancelar según las capacidades de cada transportista.
2. **Normalización de estados:** mantener el código externo original en el evento o en metadatos controlados y mapearlo explícitamente a los estados locales. No asumir que todos los proveedores comparten significados.
3. **Idempotencia y desduplicación:** usar identificador del evento externo cuando exista, o una huella estable compuesta por envío, estado, fecha y datos relevantes. Evitar insertar de nuevo el mismo evento tras cada sondeo.
4. **Tracking con ámbito:** `envios.numero_tracking` tiene un índice, pero no es único. Antes de exigir unicidad, decidir si debe serlo globalmente o por transportista y cómo tratar números nulos, reutilizados o guías maestras.
5. **Orden temporal:** procesar eventos atrasados y fuera de orden mediante `ocurrido_en`, conservando `creado_en` como fecha de recepción local. No marcar como entregado un envío por un evento antiguo que contradiga otro posterior sin una regla de resolución.
6. **Fiabilidad:** implementar sondeo de respaldo, reintentos acotados, límites de velocidad, alertas y reconciliación de envíos sin novedades.
7. **Datos de dirección y logística:** validar direcciones con el transportista sin reemplazar el snapshot del pedido. Etiquetas, manifiestos, costos reales, paquetes y evidencia de entrega no están modelados de forma específica y requieren una decisión funcional antes de ampliar el esquema.
8. **Pruebas de contrato:** simular respuestas vacías, códigos desconocidos, tracking inexistente, webhooks repetidos y eventos desordenados.

## Migraciones con Flyway

El archivo actual es una definición monolítica y no contiene una estrategia de historial de migraciones. Flyway puede aportar orden, trazabilidad y repetibilidad, pero su adopción debe planificarse:

1. **Versión inicial:** crear una migración inicial a partir del esquema corregido y comprobarla sobre una base vacía. Estado: `database/migrations/` ya contiene `V2__auth_2fa` a `V7__actualizado_en_auto`; al scaffoldear Spring, fijar `01_esquema_shopify.sql` como baseline V1 (no sigue el nombre `V1__*` de Flyway).
2. **Bases existentes:** elegir `baseline` únicamente después de comparar el esquema real con la migración inicial. Un baseline no convierte automáticamente una base divergente en compatible.
3. **Cambios inmutables:** no editar migraciones ya aplicadas y compartidas, porque se invalidan sus checksums. Las restricciones y correcciones posteriores deben incorporarse como versiones nuevas.
4. **Datos de prueba separados:** mantener seeds de desarrollo fuera de las migraciones de producción o usar ubicaciones y perfiles claramente separados. Actualmente no existe un seed en el archivo fuente; Flyway no lo crea por sí solo.
5. **Despliegue:** ejecutar `flyway validate` y `flyway migrate` con una cuenta de privilegios mínimos compatibles con el DDL, sin exponer credenciales en scripts o documentación.
6. **Compatibilidad con Supabase:** al usar Supabase, coordinar las migraciones con los objetos administrados por la plataforma y evitar mezclar cambios manuales no registrados en los mismos esquemas.
7. **Datos previos a constraints:** antes de agregar unicidad o FK nuevas, detectar duplicados, huérfanos e importes inconsistentes. Las migraciones deben incluir una estrategia de limpieza o rechazo explícita.
8. **Pruebas y recuperación:** probar migraciones sobre una copia, medir bloqueos y disponer de respaldo y procedimiento de restauración. `flyway undo` no debe darse por disponible ni suficiente para revertir pérdida de datos.

## Orden recomendado de atención

La prioridad inmediata es verificar una carga limpia en un entorno PostgreSQL real y, después, proteger credenciales y evitar cobros o reembolsos inconsistentes. Después deben atenderse la concurrencia de inventario, la integridad de envíos y la idempotencia de eventos. La optimización de índices y otras mejoras de rendimiento conviene realizarla con mediciones, sin retrasar los controles de seguridad y consistencia.
