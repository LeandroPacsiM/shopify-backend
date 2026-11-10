# ADR-017 — Trial pre-2FA estilo-Shopify (sesión staff limitada)

Fecha: 2026-11-10. Estado: aplicado (Fases 1–4). Enmienda RF-AUTH-02/03; no borra ADRs anteriores.

## Contexto

RF-AUTH-02 decía "sin enrolar no hay sesión": 6 pasos y 3 cambios de app
antes de ver valor (registro → email → 2FA → login → login/totp → tienda).
Referencia funcional: Shopify crea cuenta + tienda/trial primero y el 2FA
llega después (optativo; obligatorio al usar Shopify Payments o si la org
Plus lo impone). Fuente: `help.shopify.com` (free-trial, secure-sign-in,
two-step-authentication, requiring-secure-sign-in).

## Decisión

Sesión staff limitada pre-2FA con 2FA obligatorio al activar cobros:

- Password + email verificado bastan para JWT `mfa=pendiente`: crea tienda
  (nace STORE_OWNER), enrola TOTP, lee (GETs, catálogo). Sin migración: el
  alcance se deriva al emitir y viaja en el JWT (stateless).
- Deny-list: toda escritura bajo `/api/admin/**` con pendiente es 403;
  GET/HEAD/OPTIONS pasan. Ops ni la mira (sin roles en la limitada).
- El gate al dinero replica Shopify Payments: suscribir/activar checkout,
  pagos, pasarelas, equipo, transferencia y reset 2FA exigen JWT completo.
- Refresh re-emite limitada hasta enrolar; el reset por admin ya revocó
  todo antes (`reset_totp`), así que no quedan completas que matar.
- CUSTOMER intacto (sin TOTP, passwordless).

## Consecuencias

- RF-AUTH-02/03 enmendados; diagramas 04/12/13 actualizados.
- Riesgo aceptado: trial con tienda propia pero sin cobrar hasta el 2FA
  (equivale al trial Shopify sin checkout).
