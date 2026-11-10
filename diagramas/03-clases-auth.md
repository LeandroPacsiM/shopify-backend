# 03 — Clases de dominio: Auth, tenants y usuarios (base: `02-modelo`, `03-diccionario`, V2/V5/V8/V12; objetivo B: V20–V23, ADR-016)

```mermaid
classDiagram
    class Tienda {
        +Long id
        +String slug
        +Boolean activa
        +desactivar()
    }
    class Usuario {
        +Long id
        +String email
        +String passwordHash
        +Boolean activo
        +Boolean emailVerificado
        +String totpSecret
        +Boolean totpHabilitado
        +Integer intentosFallidos
        +Instant bloqueadoHasta
        +enrolarTotp(secreto)
        +confirmarTotp(codigo)
        +registrarFallo()
    }
    class Rol {
        +Long id
        +String nombre
    }
    class Permiso {
        +Long id
        +String nombre
    }
    class TiendaUsuario {
        +Long id
        +String rolTienda
    }
    class Cliente {
        +Long id
        +String email
        +String telefono
        +anonimizar()
    }
    class ClienteSesion {
        +Long id
        +String tokenHash
        +UUID familiaId
        +Instant expiraEn
        +Instant revocadoEn
        +rotar(nueva : ClienteSesion)
        +revocar(motivo)
        +vigente() Boolean
    }
    class CodigoCliente {
        +Long id
        +String codigoHash
        +Integer intentos
        +Instant expiraEn
        +Instant usadoEn
        +consumir()
    }
    class Sesion {
        +Long id
        +String tokenHash
        +UUID familiaId
        +Instant expiraEn
        +Instant revocadoEn
        +rotar(nueva : Sesion)
        +revocar(motivo)
        +vigente() Boolean
    }
    class CodigoRecuperacion {
        +Long id
        +String codigoHash
        +Instant usadoEn
        +consumir()
    }

    Tienda "1" --> "*" TiendaUsuario : tiene
    Usuario "1" --> "*" TiendaUsuario : rol por tienda
    Usuario "1" --> "0..1" Cliente : cuenta ÚNICA global (actual pre-B, V5)
    Cliente "*" --> "0..1" Usuario : vínculo temporal nullable (V20) → se elimina (V23)
    Usuario "1" --> "*" Sesion : sesiones staff rotadas (V8)
    Usuario "1" --> "*" CodigoRecuperacion : backup codes (V2)
    Usuario "*" --> "*" Rol : solo SUPER_ADMIN (objetivo B)
    Rol "*" --> "*" Permiso : otorga
    Tienda "1" --> "*" Cliente : aisla (V11) + email UNIQUE por tienda (V20)
    Cliente "1" --> "*" ClienteSesion : sesiones CUSTOMER (V21)
    Cliente "1" --> "*" CodigoCliente : códigos 6 dígitos (V21)
```

Notas:
- SUPER_ADMIN no es clase: es fila en `roles` + `usuario_roles` global (V12, se mantiene).
- Actual pre-B: `Cliente` implica rol global `cliente` en su tienda; `TiendaUsuario` solo `admin`/`operador`.
- Objetivo B (ADR-016): CUSTOMER sin fila en `usuario_roles`; `TiendaUsuario.rol_tienda` admite `store_owner` (exactamente 1 por tienda, V22) / `admin` / `operador`; mismo email permitido en ambos mundos sin vínculo.
- `token_legacy` no se modela: existe solo hasta el cutover V8.
