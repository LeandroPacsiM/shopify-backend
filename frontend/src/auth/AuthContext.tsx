import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { leerMfa, logout as apiLogout, refreshSesion } from '../api/auth';

interface Sesion {
  accessToken: string | null;
  roles: string[];
  email: string | null;
  mfa: 'pendiente' | 'completa' | null;
}

interface AuthCtx extends Sesion {
  guardarSesion: (accessToken: string, roles: string[], email: string) => void;
  limpiar: () => void;
  refrescar: () => Promise<boolean>;
  salir: () => Promise<void>;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion>({ accessToken: null, roles: [], email: null, mfa: null });

  const guardarSesion = useCallback((accessToken: string, roles: string[], email: string) => {
    setSesion({ accessToken, roles, email, mfa: leerMfa(accessToken) });
  }, []);

  const limpiar = useCallback(() => {
    setSesion({ accessToken: null, roles: [], email: null, mfa: null });
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
      setSesion({ accessToken: null, roles: [], email: null, mfa: null });
    }
  }, []);

  const value = useMemo(
    () => ({ ...sesion, guardarSesion, limpiar, refrescar, salir }),
    [sesion, guardarSesion, limpiar, refrescar, salir],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth fuera de AuthProvider');
  return ctx;
}
