# ADR-001: Uso de PostgreSQL

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

El sistema concentra usuarios, catálogo, inventario, pedidos, pagos, descuentos y envíos. Estas áreas comparten entidades y necesitan transacciones, consultas relacionales y reglas de integridad. Por ejemplo, crear un pedido exige conservar la relación con su checkout, sus líneas y sus direcciones; registrar un pago exige vincularlo con un pedido y una pasarela.

Además de datos estrictamente relacionales, se necesitan payloads de eventos y cambios de auditoría con estructura variable. Los importes monetarios no admiten errores de representación propios de números de coma flotante. La solución debe poder ejecutarse localmente y desplegarse en una plataforma PostgreSQL administrada como Supabase sin cambiar de motor.

## Decisión

Se adopta **PostgreSQL** como motor de base de datos del sistema y como lenguaje de definición de la fuente de verdad `database/01_esquema_shopify.sql`.

La decisión incluye:

- `BIGSERIAL` para identificadores generados y claves primarias relacionales.
- Claves foráneas con acciones `CASCADE`, `SET NULL` o `RESTRICT` según la relación.
- Restricciones `UNIQUE` y `CHECK` para los invariantes que el esquema sí expresa directamente.
- `NUMERIC(12,2)` para importes y `NUMERIC(5,2)` para porcentajes, evitando `FLOAT` en dinero.
- `TIMESTAMPTZ` para fechas con zona horaria y `JSONB` para payloads y datos de auditoría de estructura variable.
- Índices secundarios para las consultas y relaciones previstas.
- Supabase como plataforma compatible de alojamiento y operación administrada cuando se requiera. Supabase no se considera otro motor ni sustituye las constraints, las migraciones, las copias de seguridad o la validación de la aplicación.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| MySQL o MariaDB | Amplia disponibilidad, buen soporte relacional y experiencia operativa común. | Cambia tipos, funciones y comportamiento de algunas constraints; el uso previsto de `JSONB` y otras construcciones es específico de PostgreSQL. | No aporta una ventaja suficiente para justificar reescribir el esquema y renunciar a las capacidades PostgreSQL seleccionadas. |
| MongoDB como base principal | Documentos flexibles y facilidad para representar payloads variables. | Las relaciones y transacciones entre pedidos, pagos, líneas e inventario requerirían otro diseño y más controles de aplicación. | La flexibilidad documental no compensa la pérdida de un modelo relacional natural para el núcleo transaccional. |
| SQLite | Configuración mínima y archivo local sencillo para demostraciones. | Menor adecuación para concurrencia de escritura, operación multiusuario y despliegue administrado del sistema completo. | Es útil para herramientas acotadas, pero no satisface los requisitos de operación concurrente del comercio. |
| Combinar una base relacional con un documental desde el inicio | Permite elegir un motor especializado para cada tipo de dato. | Duplica operación, sincronización, seguridad, respaldos y puntos de fallo. | `JSONB` cubre la necesidad variable actual sin introducir complejidad de persistencia políglota prematura. |

## Consecuencias

### Positivas

- Las FK y transacciones protegen relaciones esenciales entre las 54 tablas del dominio.
- `NUMERIC` permite cálculos monetarios exactos con reglas de redondeo explícitas.
- `JSONB` conserva payloads de pago y auditoría sin convertir todo el modelo en documentos flexibles.
- `TIMESTAMPTZ` evita ambigüedad de zona horaria en eventos de pedidos, pagos y tracking.
- El mismo motor sirve para desarrollo local, pruebas automatizadas y despliegue en Supabase.
- Las constraints existentes documentan parte de las reglas de negocio cerca de los datos.

### Negativas

- El uso de `JSONB`, `BIGSERIAL` y sintaxis PostgreSQL aumenta el costo de migrar a otro motor.
- Las constraints no cubren todas las reglas de negocio; totales, cronología, concurrencia e idempotencia siguen exigiendo lógica y pruebas adicionales.
- Los importes `NUMERIC` requieren decisiones consistentes de redondeo e impuestos en la aplicación.
- Supabase reduce administración de infraestructura, pero añade límites de servicio, gestión de conexiones y dependencia de un proveedor.
- Una operación incorrecta con privilegios elevados o una migración defectuosa puede afectar a todos los módulos que comparten el motor.
