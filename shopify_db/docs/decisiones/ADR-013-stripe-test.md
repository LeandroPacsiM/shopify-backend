# ADR-013: Stripe test detrás del adapter (RF-PAY-01)

## Estado

Aceptado. Verificado en vivo 2026-11-08 (`StripeVivoIT`: crear + cancelar PaymentIntent con el adapter contra `api.stripe.com` en modo test, llave solo en memoria y borrada después). E2E con webhook real verificado 2026-11-08 (`StripePagoVivoIT`: intento → confirm con visa → `payment_intent.succeeded` vía CLI `listen` → pedido `pagado`; `allow_redirects=never` por confirmación en servidor).

## Fecha

2026-11-08.

## Contexto

Solo existía `MockAdapter`; el contrato `PaymentAdapter` ya preveía
"Stripe test después" con credenciales por tienda. Para el e2e real
hacen falta `sk_test_` por tienda (cuenta existente reutilizable) y
`whsec_` vía Stripe CLI; nada de eso vive en el repo.

## Decisión

`StripeAdapter` (código `stripe`, solo modo `test`): PaymentIntents con
`automatic_payment_methods` + `Idempotency-Key`, refunds por
`payment_intent`, montos a céntimos, Bearer con la `sk` de la tienda
descifrada en memoria (jamás a logs). `secretoCifrado` guarda
`sk_test_...` o `sk_test_...;whsec_...`; la firma se verifica con el
esquema real (`t,v1` HMAC-SHA256) y sin `whsec` se rechaza. Base URL
configurable (`stripe.api-base`) para probar con stub. Tests 5/5 contra
stub HTTP local, sin llaves reales. E2E real: registrar `tienda_pasarela`
con secreto cifrado + `stripe listen --forward-to`.

## Alternativas consideradas

- SDK stripe-java: rechazado; HttpClient basta (como Resend) y evita una
  dependencia pesada.
- Columna `whsec` propia: rechazado por ahora; el formato `sk;whsec`
  evita una migración hasta que el e2e lo pida.
- Checkout Sessions: rechazado; nuestro checkout ya existe como entidad,
  PaymentIntents calza (patrón Stripe para merchants con checkout propio).

## Consecuencias

### Positivas

- Stripe real conectable por tienda sin cambiar `PagoService` (el mapa
  por `codigo()` lo resuelve).
- Sin secretos en repo/tests: el stub usa llaves ficticias.

### Negativas

- Modo `vivo` aún bloqueado.
- Registro por endpoint admin cerrado (`POST /api/admin/pasarelas`,
  solo `admin`, secreto cifrado, re-registrar rota).
- Traductor cerrado (`POST /api/webhooks/pagos/stripe`: payment/refund
  events → flujo idempotente + cotejo de monto; tipos raros → 200).
