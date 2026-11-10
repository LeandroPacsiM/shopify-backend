# Demo visual de Auth (sin build)

Página estática que muestra los dos flujos punta a punta contra el backend local.
No es el SPA final: es una ayuda para la presentación.

## Requisitos

1. Backend levantado con perfil `local` (migra demo y expone `:8080`):
   ```powershell
   cd backend
   $env:PGPASSWORD='1234'
   ./mvnw spring-boot:run '-Dspring-boot.run.profiles=local'
   ```
2. Servir esta carpeta en un origen permitido por el CORS (`5173` o `3000`):
   ```powershell
   cd frontend
   python -m http.server 5173
   ```
   Abrir `http://localhost:5173/demo-auth.html`. No abrir como `file://`
   (las cookies HttpOnly de refresh no funcionan sin origen http).

## Guion (historia de Pepito y María)

Staff: registra → confirma email (código del log) → inicia TOTP (pega la URL
otpauth en tu app o mete el secreto a mano) → confirma → login (challenge) →
login/totp (JWT en memoria) → crea tienda (nace STORE_OWNER).

CUSTOMER: solicitar (código del log, el perfil aún no existe) → verificar
(nace el perfil + JWT).

## Notas

- JWTs solo en memoria del JS, nunca en `localStorage`.
- Refresh via cookies HttpOnly (`refresh` staff, `refresh_customer` CUSTOMER).
- Códigos de email y CUSTOMER salen en el log del backend (modo prueba).
