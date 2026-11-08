# ADR-002: Una única base relacional sin Redis

## Estado

Aceptado

## Fecha

2026-10-28

## Contexto

Usuarios, clientes, productos, variantes, inventario, carritos, checkout, pedidos, pagos, descuentos y envíos necesitan consultar y modificar datos compartidos. Separar estos datos desde el inicio impediría usar FK entre módulos y obligaría a coordinar consistencia entre varios almacenes antes de conocer la carga real.

También podría incorporarse Redis para sesiones, caché de catálogo, bloqueos o reservas. Sin embargo, todavía no existen mediciones que demuestren que PostgreSQL no satisface esas necesidades, ni una estrategia de invalidación y recuperación para mantener copias derivadas. Introducir Redis ahora aumentaría la infraestructura y crearía dudas sobre cuál almacén es la fuente de verdad.

## Decisión

Se mantiene **una única base relacional PostgreSQL** como fuente de verdad transaccional para todos los módulos del sistema. Las tablas comparten esquema, conexiones lógicas, migraciones, respaldos y transacciones.

**No se utiliza Redis** en la arquitectura actual. Sesiones, carritos, stock, estados de pedido y eventos se persisten en sus tablas relacionales. No se debe tratar una caché externa, añadida informalmente por algún componente, como dato autoritativo.

Esta decisión no impide una revisión futura basada en métricas. Si más adelante se incorpora Redis, deberá limitarse inicialmente a datos derivados, efímeros o recuperables, con políticas explícitas de expiración, invalidación y reconstrucción.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas | Razón de rechazo |
|---|---|---|---|
| Una base por módulo o microservicio | Aísla cargas y despliegues, y permite evolucionar esquemas por separado. | Elimina FK entre módulos, complica consultas y exige consistencia distribuida para una compra. | El sistema no tiene todavía límites organizativos ni de escala que justifiquen el costo distribuido. |
| PostgreSQL más Redis desde el inicio | Puede reducir lecturas repetidas y ofrecer estructuras rápidas para sesiones o bloqueos. | Añade otro servicio, invalidación de caché, riesgo de datos divergentes y nuevas políticas de seguridad y respaldo. | No hay evidencia de cuello de botella y sí un costo cierto de complejidad. |
| Redis como almacén principal de sesiones y carritos | Acceso rápido y expiración automática mediante TTL. | Requiere decidir persistencia, recuperación y sincronización con pedidos y clientes relacionales. | Carritos y sesiones ya están modelados en PostgreSQL y no se quiere dividir su fuente de verdad. |
| Base NoSQL única | Esquema flexible y documentos agregados para algunas vistas. | Las operaciones transaccionales y consultas entre dominios se vuelven menos directas. | No conserva las ventajas relacionales que sustentan el diseño actual. |

## Consecuencias

### Positivas

- Una transacción puede abarcar entidades de varios módulos sin coordinación distribuida.
- Las 79 FK preservan relaciones directas y facilitan consultas de extremo a extremo.
- Desarrollo, pruebas, migraciones, respaldo y restauración requieren una sola fuente de datos.
- No existen problemas de caché desactualizada ni divergencia entre Redis y PostgreSQL.
- La depuración es más sencilla porque el estado persistente se encuentra en un único sistema.

### Negativas

- La base concentra carga, disponibilidad y riesgo operativo de todos los módulos.
- Las lecturas repetidas no cuentan con una caché dedicada, por lo que el rendimiento depende de índices, consultas y conexiones bien gestionados.
- Un pico de trabajos analíticos o administrativos puede afectar a las operaciones transaccionales.
- Las reservas y bloqueos de concurrencia deben resolverse con transacciones y mecanismos PostgreSQL.
- Separar un módulo en el futuro exigirá extraer datos, revisar dependencias y reemplazar consultas que hoy aprovechan las FK.
