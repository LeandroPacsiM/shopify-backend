# ADR-010: Tokens de cuenta (V15, RF-AUTH-07)

## Estado

Aceptado.

## Fecha

2026-11-08.

## Contexto

V2 dejó `tokens_cuenta` como pendiente y el registro creaba usuarios con
`email_verificado=FALSE` sin forma de verificarlo ni de recuperar la
contraseña. Sin esto, cualquier email vale y una contraseña olvidada
bloquea la cuenta (el reset 2FA de admin no cubre password).

## Decisión

Migración `V15__tokens_cuenta`: tabla `tokens_cuenta`
(`usuario_id` CASCADE, `tipo` CHECK verificacion/reset, `token_hash`
UNIQUE, `expira_en`, `usado_en`). Solo se guarda SHA-256; el plano viaja
por correo (Resend HTTPS, modo log sin llave) y jamás va a logs.
Backend: verificación 24h, reset 60min; re-solicitar invalida pendientes
del mismo tipo; confirmar es un solo uso; el reset además revoca todas
las sesiones y desbloquea. Solicitar responde genérico (no enumerar).
Endpoints: `POST /auth/email/solicitar|confirmar`,
`POST /auth/password/solicitar|confirmar`. El registro emite el token de
verificación en la misma transacción.

## Alternativas consideradas

- Token en claro en BD: rechazado; filtra con un backup/SELECT.
- JWT con expiración como token: rechazado; no se invalida por re-solicitud
  sin lista de revocación (equivale a la tabla).
- Devolver el token en la respuesta: rechazado; anula el propósito del email.

## Consecuencias

### Positivas

- Email verificable y contraseña recuperable sin ayuda de admin.
- Reset expulsa sesiones (robo de sesión no sobrevive al reset).

### Negativas

- En dev (modo log) el token queda en el log como "bandeja local";
  en prod con llave Resend va por correo real.
- Desde esta revisión, login (paso 1 y 2) exige email verificado; el
  enrolamiento TOTP puede completarse antes (es parte del alta).
