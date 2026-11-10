# SPA staff (React + Vite) — prueba visual del backend

Rutas: `/login` → `/registro` → `/email` → trial → `/tiendas/nueva` →
`/totp` (2FA obligatorio antes de cobrar) → `/admin`. El JWT vive solo en
memoria (`AuthContext`); el refresh va en cookie HttpOnly.

## Uso local

1. Backend con perfil `local` (migra demo y expone `:8080`):
   ```powershell
   cd backend
   ./mvnw spring-boot:run '-Dspring-boot.run.profiles=local'
   ```
2. SPA (proxy `/auth` y `/api` → `localhost:8080`, ver `vite.config.ts`):
   ```powershell
   cd frontend
   npm run dev
   ```
   Abrir `http://localhost:5173`.

## Guion trial (ADR-017)

Registro → email (código del correo) → login → **modo trial** (JWT
`mfa=pendiente`: crea tienda + lee, la escritura en admin es 403) →
`/totp` (QR + backup codes) → login con código (JWT `mfa=completa`) →
suscribir y operar. La insignia Trial/Completa sale del claim `mfa`
(solo UI, la autorización real la hace el backend).
