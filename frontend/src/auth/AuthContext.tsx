import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { leerMfa, logout as apiLogout, refreshSesion } from '../api/auth';

interface Sesion {
  accessToken: string | null;
  roles: string[];
  email: string | null;
  mfa: 'pendiente' | 'completa' | null;
}

interface AuthCtx extends Sesion {
  restaurando: boolean;
  guardarSesion: (accessToken: string, roles: string[], email: string) => void;
  limpiar: () => void;
  refrescar: () => Promise<boolean>;
  salir: () => Promise<void>;
}

const Ctx = createContext<AuthCtx | null>(null);

const EMAIL_KEY = 'plataforma.email';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion>({ accessToken: null, roles: [], email: null, mfa: null });
  const [restaurando, setRestaurando] = useState(true);

  const guardarSesion = useCallback((accessToken: string, roles: string[], email: string) => {
    try {
      sessionStorage.setItem(EMAIL_KEY, email);
    } catch {
      /* sin storage: la sesión sigue en memoria */
    }
    setSesion({ accessToken, roles, email, mfa: leerMfa(accessToken) });
  }, []);

  const limpiar = useCallback(() => {
    try {
      sessionStorage.removeItem(EMAIL_KEY);
    } catch {
      /* nada */
    }
    setSesion({ accessToken: null, roles: [], email: null, mfa: null });
  }, []);

  // Al abrir: si hay email guardado, intenta restaurar por refresh (cookie).
  // Sin cookie válida queda en login, sin rebote visible.
  useEffect(() => {
    let viva = true;
    (async () => {
      try {
        const email = sessionStorage.getItem(EMAIL_KEY);
        if (email) {
          const r = await refreshSesion();
          if (viva) {
            setSesion({ accessToken: r.accessToken, roles: r.roles, email, mfa: leerMfa(r.accessToken) });
          }
        }
      } catch {
        /* sin sesión: las páginas mandan a /login */
      } finally {
        if (viva) setRestaurando(false);
      }
    })();
    return () => {
      viva = false;
    };
  }, []);

  const refrescar = useCallback(async () => {
    try {
      const r = await refreshSesion();
      setSesion((s) => ({ ...s, accessToken: r.accessToken, roles: r.roles, mfa: leerMfa(r.accessToken) }));
      return true;
    } catch {
      return false;
    }
  }, []);

  const salir = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      try {
        sessionStorage.removeItem(EMAIL_KEY);
      } catch {
        /* nada */
      }
      setSesion({ accessToken: null, roles: [], email: null, mfa: null });
    }
  }, []);

  const value = useMemo(
    () => ({ ...sesion, restaurando, guardarSesion, limpiar, refrescar, salir }),
    [sesion, restaurando, guardarSesion, limpiar, refrescar, salir],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
