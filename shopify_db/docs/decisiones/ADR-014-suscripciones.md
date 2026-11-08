# ADR-014: Suscripción de plataforma por tienda (V18, RF-BILL)

## Estado

Aceptado.

## Fecha

2026-11-08.

## Contexto

Shopify verificado (AGENTS.md): plan POR TIENDA, dinero del comprador
siempre a la cuenta del merchant, impago = tienda congelada. Nuestra
`tiendas.activa` ya permitía suspender manual, pero nada ligaba el pago
del software ni bloqueaba ventas sin pagar.

## Decisión

Migración `V18__suscripciones`: `suscripciones` (UNIQUE por tienda,
plan/estado CHECK, ids de Stripe para V19, `periodo_fin`) +
`suscripcion_eventos` (dedup). Servicio con ver/suscribir/cancelar +
webhook propio (firma `FirmaStripe` compartida con pagos + dedup):
pagada → activa + periodo (+30/+365) + descongela; vencida → congela.
Enforcement en `CheckoutService.crear` (sin vigente no hay checkout
nuevo). Ops lista el plan/estado como agregado. V19 suma el cobro real:
customer + subscription con priceIds por entorno al suscribir,
`invoice.payment_succeeded` renueva y `subscription.deleted` cancela;
sin configurar queda pendiente. Verificado en vivo 2026-11-08
(`StripeFacturacionVivoIT`: customer + subscription `incomplete` reales
con price validado por intervalo, suscripción cancelada al final;
llaves solo en memoria y borradas).

## Alternativas consideradas

- Comisión por venta vía Connect: rechazado por ahora; onboarding por
  tienda complejo, la suscripción fija calza con "cobrar por el software".
- Congelar también lecturas: aplicado después (revirtió el rechazo V18):
  `TenantFilter` bloquea tienda inactiva en todo salvo su facturación,
  para pagar y reactivar (verificado en `TiendaCongeladaTest`).

## Consecuencias

### Positivas

- Modelo de negocio completo: plan por tienda + pasarela propia por tienda.
- Webhook idempotente y firma compartida en un solo util.

### Negativas

- Sin Stripe real, la activación es vía webhook demo/tests; tiendas
  viejas sin suscripción quedan bloqueadas (seed demo debe crearla).
