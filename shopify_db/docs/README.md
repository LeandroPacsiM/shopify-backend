# shopify_db

Documentación de la base de datos de un comercio electrónico tipo Shopify. El repositorio toma como referencia un backend previsto en Java Spring Boot, PostgreSQL administrado en Supabase y el despliegue de la aplicación en Render.

## Ficha de la línea base

| Concepto | Valor |
| --- | --- |
| Nombre | `shopify_db` |
| Versión del esquema | **1.0**, como línea base documental; el SQL no declara una versión interna |
| Motor | PostgreSQL |
| Fuente de verdad del esquema | [`database/01_esquema_shopify.sql`](../database/01_esquema_shopify.sql) |
| Alcance estructural verificado | 59 tablas, 86 claves foráneas y 71 índices explícitos (V1 + migraciones V2–V19; validado en la BD local) |

La versión **1.0** identifica únicamente esta línea base de la documentación. El archivo SQL no declara una versión interna del esquema ni una tabla de versiones.

## Documentos

Los diez documentos numerados componen la línea base funcional y técnica:

1. [Arquitectura](01-arquitectura.md): objetivo, alcance, componentes, despliegue e integraciones.
2. [Modelo de datos](02-modelo-datos.md): catálogo completo de tablas, relaciones y diagramas ER por módulo.
3. [Diccionario de datos](03-diccionario-datos.md): columnas, tipos, nulabilidad, valores predeterminados y restricciones.
4. [Reglas de negocio](04-reglas-negocio.md): reglas del dominio y responsabilidades de validación del backend.
5. [Flujos principales](05-flujos-principales.md): recorridos de identidad, compra, pedido, pago y entrega.
6. [Seguridad e integridad](06-seguridad-e-integridad.md): autenticación, autorización, constraints, índices y auditoría.
7. [Datos de prueba](07-datos-prueba.md): escenarios y conjunto mínimo de datos ficticios para demostración.
8. [Observaciones y riesgos](08-observaciones.md): limitaciones, decisiones pendientes, integraciones y migraciones.
9. [Recorrido guiado](09-recorrido-guiado.md): la historia de una compra en 9 pasos, con queries para pgAdmin.
10. [Requerimientos](10-requerimientos.md): RF y RNF completos con estado (BD/BE/FE).

## Decisiones de arquitectura

Los ADR registran decisiones y sus consecuencias; no sustituyen al esquema SQL. La migración repetible `R__roles_base.sql` inserta los roles del RBAC (`super_admin`, `admin`, `operador`, `cliente`); es dato de referencia aplicado por Flyway, no seed de demostración.

1. [ADR-001: Uso de PostgreSQL](decisiones/ADR-001-uso-postgresql.md).
2. [ADR-002: Una única base relacional sin Redis](decisiones/ADR-002-base-unica.md).
3. [ADR-003: Productos, variantes y opciones](decisiones/ADR-003-producto-variantes.md).
4. [ADR-004: Snapshot histórico en las líneas de pedido](decisiones/ADR-004-snapshot-pedido-items.md).
5. [ADR-005: Múltiples pagos, eventos y reembolsos](decisiones/ADR-005-pagos-multiples.md).
6. [ADR-006: Envíos y tracking mediante eventos](decisiones/ADR-006-tracking-eventos.md).
7. [ADR-007: Endurecimiento auth e integridad V2–V7](decisiones/ADR-007-endurecimiento-v2-v7.md).
8. [ADR-008: Sesiones rotadas, snapshot de impuestos y mock de pagos V8–V9](decisiones/ADR-008-sesiones-impuestos-mock.md).
9. [ADR-009: Multi-tenant multi-sucursal V10–V12](decisiones/ADR-009-multitenant.md).

## Mantenimiento

1. Aplicar primero cualquier cambio estructural en [`database/01_esquema_shopify.sql`](../database/01_esquema_shopify.sql); ese archivo es la única fuente de verdad para tablas, columnas, restricciones, relaciones e índices.
2. Actualizar en la misma revisión el [modelo de datos](02-modelo-datos.md) y el [diccionario](03-diccionario-datos.md): inventario de tablas, campos, cardinalidades, diagramas y recuentos de objetos.
3. Revisar las [reglas de negocio](04-reglas-negocio.md), los [flujos](05-flujos-principales.md) y la [seguridad e integridad](06-seguridad-e-integridad.md) cuando cambien constraints o responsabilidades del backend.
4. Revisar la [arquitectura](01-arquitectura.md) cuando cambien componentes, responsabilidades, proveedores externos o el destino de despliegue.
5. Actualizar los [datos de prueba](07-datos-prueba.md) y registrar limitaciones nuevas en [observaciones](08-observaciones.md).
6. Crear un ADR nuevo para una decisión que reemplace o modifique una decisión anterior, conservando el historial.
7. Comprobar antes de publicar que los enlaces relativos funcionan, que los bloques Mermaid se renderizan y que la documentación no introduce objetos ausentes del SQL.
8. Incrementar la línea base documental cuando se publique un conjunto de cambios documentales, sin presentar ese número como una versión declarada por la base de datos.
