# 04 — Secuencia Auth + 2FA (base: RF-AUTH, Flujo 1 de `05`, V2/V8; objetivo B: RF-AUTH-09/10, ADR-016)

> Secciones "staff": estado actual. Sección "CUSTOMER": objetivo B (flag `auth.customer.enabled=false` hasta el cutover).

## Registro staff + enrolamiento (actual; objetivo B: `POST /auth/registro-administrativo` sin permisos)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant C as AuthController
    participant S as AuthService
    participant R as Repositorios
    participant D as PostgreSQL

    U->>C: POST /auth/registro (email, password)
    C->>S: registrar()
    S->>R: existePorEmail? (409 si sí)
    S->>D: TX: usuario + cliente + rol inicial
    S->>S: genera secreto TOTP (cifrado)
    C-->>U: 201 + QR otpauth://
    U->>C: POST /auth/totp/confirmar (código)
    C->>S: confirmar() + genera 10 backup codes (hash)
    S->>D: TX: habilitado=TRUE + confirmado_en + códigos
    C-->>U: 200 enrolado (códigos una sola vez)
```

## Login staff + refresh (actual; `aud=tienda-admin` en objetivo B)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant C as AuthController
    participant S as AuthService
    participant D as PostgreSQL

    U->>C: POST /auth/login (email, password)
    C->>S: passwordOK? (genérico si no, +intento, bloqueo si toca)
    S->>S: email verificado? (409 si no; enrolar sí pudo antes)
    C-->>U: 200 challenge TOTP requerido
    U->>C: POST /auth/totp/verificar (código o backup code)
    C->>S: verifica (ventana ±1 o hash + usado_en)
    S->>D: TX: resetea intentos + ultimo_acceso + sesión (token_hash, familia nueva)
    C-->>U: JWT 15min + refresh opaco (HttpOnly)
    U->>C: POST /auth/refresh (refresh)
    C->>S: hash vigente y no revocado?
    S->>D: TX: revoca vieja (reemplazado_por) + crea nueva misma familia
    C-->>U: JWT + refresh nuevos
    Note over C,D: reuso de refresh rotado = revocar familia entera
```

## Auth CUSTOMER passwordless (objetivo B, RF-AUTH-10, `aud=tienda-storefront`)

```mermaid
sequenceDiagram
    actor C as Customer
    participant CC as ClienteAuthController
    participant S as ClienteAuthService
    participant D as PostgreSQL

    C->>CC: POST /auth/cliente/solicitar (tiendaSlug, email)
    CC->>S: rate-limit (tienda,email,IP) + intervalo 60s
    S->>D: TX: invalida previos + guarda HMAC(código 6 dígitos, TTL 10min)
    C-->>C: código por correo (perfil AÚN no creado)
    C->>CC: POST /auth/cliente/verificar (código)
    S->>S: hash HMAC OK? (5 intentos, FOR UPDATE, sin bloqueo permanente)
    S->>D: TX: crea Cliente si no existe + sesión (token_hash, familia nueva)
    CC-->>C: JWT 15min (`aud=tienda-storefront`) + refresh 7 días rotado
    Note over CC,D: mismo email permitido en staff y CUSTOMER, incluso misma tienda
```

## Reset admin (RF-AUTH-06)

```mermaid
sequenceDiagram
    actor A as SuperAdmin/Admin
    participant C as AuthController
    participant S as AuthService
    participant D as PostgreSQL

    A->>C: POST /api/.../usuarios/:id/2fa/reset
    C->>S: autoriza (super_admin o admin, auditado)
    S->>D: TX: habilitado=FALSE + secreto NULL + auditoría
    C-->>U: usuario re-enrola con QR (flujo registro)
```

## Crear tienda / onboarding (RF-TENANT-04)

```mermaid
sequenceDiagram
    actor M as Comerciante
    participant C as AuthController
    participant S as TiendaOnboardingService
    participant D as PostgreSQL

    M->>C: POST /auth/tiendas (JWT verificado + TOTP, nombre, slug)
    C->>S: crear(usuarioId)
    S->>D: slug único? (409 si existe)
    S->>D: TX: tienda activa + vínculo STORE_OWNER + suscripción pendiente + auditoría
    C-->>M: 201 tienda (sin checkout hasta suscribir/activar)
    Note over C,D: un usuario puede crear varias tiendas
```

## Verificación email + reset (RF-AUTH-07, V15 `tokens_cuenta`)

```mermaid
sequenceDiagram
    actor U as Usuario
    participant C as AuthController
    participant S as AuthService
    participant D as PostgreSQL

    U->>C: POST /auth/registro (email, password)
    C->>S: registrar() + emite token verificación (24h, solo hash)
    S->>D: TX: usuario + cliente + rol + token_cuenta
    S-->>U: correo con código (Resend HTTPS)
    U->>C: POST /auth/email/confirmar (código)
    C->>S: tipo + vigente + un solo uso?
    S->>D: TX: email_verificado=TRUE + usado_en
    U->>C: POST /auth/password/solicitar (email)
    C-->>U: 204 genérico (no revela si existe)
    S->>D: TX: invalida previos + token reset (60min)
    U->>C: POST /auth/password/confirmar (código + nueva)
    S->>D: TX: password nuevo + revoca sesiones + usado_en
    Note over C,D: re-solicitar invalida el anterior; reuso = 401
```
