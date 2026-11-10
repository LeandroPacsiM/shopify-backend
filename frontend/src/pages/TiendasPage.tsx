import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { misTiendas } from '../api/auth';
import type { MiTienda } from '../api/auth';
import { useAuth } from '../auth/AuthContext';

/** Mis tiendas: membresías del comerciante (funciona en trial). */
export default function TiendasPage() {
  const { accessToken, restaurando } = useAuth();
  const [tiendas, setTiendas] = useState<MiTienda[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    misTiendas(accessToken)
      .then(setTiendas)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'No se pudo cargar'));
  }, [accessToken]);

  if (restaurando) return <p className="muted">Restaurando sesión…</p>;
  if (!accessToken) return <Navigate to="/login" replace />;

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/admin">← Admin</Link>
        <span>Mis tiendas</span>
        <Link to="/tiendas/nueva">+ Nueva</Link>
      </nav>
      <section className="card narrow">
        <h2>GET /auth/tiendas</h2>
        {error && <p className="banner banner-error">{error}</p>}
        {!tiendas && !error && <p className="muted">Cargando…</p>}
        {tiendas && tiendas.length === 0 && (
          <p className="muted">Aún no tienes tiendas. <Link to="/tiendas/nueva">Crea la primera</Link>.</p>
        )}
        {tiendas && tiendas.length > 0 && (
          <ul>
            {tiendas.map((t) => (
              <li key={t.id}>
                <b>{t.nombre}</b> <span className="mono small">{t.slug}</span>{' '}
                <span className="muted small">{t.rolTienda} · {t.activa ? 'activa' : 'suspendida'}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
