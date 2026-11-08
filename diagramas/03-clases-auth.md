# 03 — Clases de dominio: Auth, tenants y usuarios (base: `02-modelo`, `03-diccionario`, V2/V5/V8/V12)

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
        +String telefono
        +anonimizar()
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
    Usuario "1" --> "*" Cliente : cuenta (RF-TENANT, V5)
    Usuario "1" --> "*" Sesion : sesiones rotadas (V8)
    Usuario "1" --> "*" CodigoRecuperacion : backup codes (V2)
    Usuario "*" --> "*" Rol : globales incl. super_admin
    Rol "*" --> "*" Permiso : otorga
    Tienda "1" --> "*" Cliente : aisla (V11)
```

Notas:
- `super_admin` no es clase: es fila en `roles` + `usuario_roles` global (V12).
- `Cliente` implica rol cliente en su tienda; `TiendaUsuario` solo `admin`/`operador`.
- `token_legacy` no se modela: existe solo hasta el cutover V8.
