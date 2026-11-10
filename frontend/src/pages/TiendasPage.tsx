import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { misTiendas } from '../api/auth';
import type { MiTienda } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import AdminShell from '../components/AdminShell';

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
    <AdminShell>
      <div className="page-head">
        <div>
          <h2>Mis tiendas</h2>
          <p className="muted small" style={{ margin: '4px 0 0' }}>
            Elige con cuál trabajar.
          </p>
        </div>
        <Link to="/tiendas/nueva" className="btn btn-primary" style={{ width: 'auto' }}>
          + Nueva
        </Link>
      </div>
      <div className="card">
        {error && <p className="banner banner-error">{error}</p>}
        {!tiendas && !error && <p className="muted">Cargando…</p>}
        {tiendas && tiendas.length === 0 && (
          <p className="muted">Aún no tienes tiendas. <Link to="/tiendas/nueva">Crea la primera</Link>.</p>
        )}
        {tiendas && tiendas.length > 0 && (
          <ul className="tienda-list">
            {tiendas.map((t) => (
              <li key={t.id}>
                <b>{t.nombre}</b> <span className="mono small">{t.slug}</span>{' '}
                <span className="muted small">{t.rolTienda} · {t.activa ? 'activa' : 'suspendida'}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </AdminShell>
  );
}
