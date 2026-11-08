# ADR-005: Múltiples pagos, eventos y reembolsos

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

Un pedido puede tener varios intentos de pago: el primero puede ser rechazado, el siguiente aprobado y, posteriormente, puede producirse un reembolso parcial o total. También puede cambiar de método o proveedor. Una única fila de pago por pedido borraría ese recorrido o mezclaría operaciones externas diferentes.

Las pasarelas comunican resultados mediante respuestas de API y webhooks, a veces duplicados o fuera de orden. Se necesita conservar el estado actual, el historial de cambios y el payload recibido para diagnóstico. Stripe, Culqi, Yape y PayPal son servicios externos con contratos propios; no son bases de datos internas del sistema ni justifican una base separada por proveedor.

## Decisión

Se modela el dominio con `pasarelas_pago`, `pagos`, `pago_eventos` y `reembolsos`:

- `pasarelas_pago` es el catálogo local de proveedores y su estado de habilitación, no un almacén de credenciales.
- `pagos` permite múltiples intentos u operaciones por pedido y conserva monto, moneda, método, estado e identificador externo.
- `pago_eventos` registra cambios de estado, tipo, referencia y payload `JSONB` para trazabilidad.
- `reembolsos` vincula el pedido y el pago, y registra importe, moneda, motivo, estado e identificador externo.

Stripe, Culqi, Yape y PayPal se integran mediante **APIs y webhooks externos**, usando adaptadores de aplicación. No se crea una base de datos por pasarela. Las claves privadas se guardan en un gestor de secretos o configuración protegida, nunca en estas tablas ni en payloads. Los datos de tarjeta deben tokenizarse mediante el proveedor y no almacenarse localmente.

El procesamiento real debe ser idempotente, verificar firmas, conciliar estados y validar que cada reembolso pertenezca al mismo pedido y no exceda el saldo cobrado. El esquema actual no impone todas esas reglas.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| Un solo pago por pedido | Modelo pequeño y consulta directa del estado final. | Pierde intentos rechazados, cambios de pasarela y trazabilidad de operaciones sucesivas. | No representa reintentos ni el ciclo real de cobro. |
| Guardar solo el estado actual del pago | Reduce filas y procesamiento de historial. | No conserva el origen del cambio ni el payload necesario para diagnosticar webhooks. | La auditoría y reconciliación serían insuficientes. |
| Una base de datos por pasarela | Aísla físicamente los registros de cada proveedor. | Impide FK uniformes con pedidos y multiplica consultas, seguridad, migraciones y respaldos. | Los proveedores son servicios externos, no dominios que requieran bases internas separadas. |
| Tablas específicas para Stripe, Culqi, Yape y PayPal | Permite columnas particulares para cada API. | Fragmenta la lógica común y obliga a modificar el esquema al agregar proveedores. | Un modelo común con adaptadores y payloads acotados cubre mejor la interoperabilidad. |
| Guardar directamente los datos completos de tarjeta | Evita depender de tokens para ciertos procesos locales. | Expone datos altamente sensibles y amplía obligaciones de seguridad y cumplimiento. | Es un riesgo innecesario; se utiliza tokenización del proveedor. |

## Consecuencias

### Positivas

- Un pedido conserva intentos fallidos y exitosos sin sobrescribir su historia.
- El catálogo de pasarelas permite habilitar proveedores sin crear bases ni tablas específicas.
- `pago_eventos` facilita diagnóstico, auditoría y conciliación de respuestas y webhooks.
- Los reembolsos parciales y totales se representan como operaciones separadas del cobro original.
- `JSONB` admite diferencias de payload entre proveedores sin flexibilizar los importes y relaciones principales.

### Negativas

- La aplicación debe reconciliar estados locales con sistemas externos y tratar duplicados, desorden y tiempos de espera.
- `transaccion_externa_id` no es único en pagos ni reembolsos, por lo que la base actual no evita duplicar operaciones externas.
- Las FK independientes de `reembolsos` no garantizan que pago y pedido coincidan, ni existe un tope acumulado de reembolso.
- Guardar payloads completos puede introducir datos sensibles si no se filtran y protegen.
- La consistencia entre pago, pedido, inventario y reembolso requiere transacciones, idempotencia y diseño de mensajería adicionales.
