import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { crearTienda, refreshSesion } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import AdminShell from '../components/AdminShell';

export default function CrearTiendaPage() {
  const { accessToken, email, guardarSesion } = useAuth();
  const [nombre, setNombre] = useState('');
  const [slug, setSlug] = useState('');
  const [descripcion, setDescripcion] = useState('');
  const [creada, setCreada] = useState<{ id: number; nombre: string; slug: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  if (!accessToken) return <Navigate to="/login" replace />;

  async function intentar(token: string) {
    return crearTienda(token, {
      nombre: nombre.trim(),
      slug: slug.trim().toLowerCase(),
      descripcion: descripcion.trim() || undefined,
    });
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const t = await intentar(accessToken as string);
      setCreada({ id: t.id, nombre: t.nombre, slug: t.slug });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'No pudimos crear tu tienda';
      if (/401|sesión|token/i.test(msg)) {
        try {
          const r = await refreshSesion();
          guardarSesion(r.accessToken, r.roles, email ?? '');
          const t = await intentar(r.accessToken);
          setCreada({ id: t.id, nombre: t.nombre, slug: t.slug });
          return;
        } catch (err2) {
          setError(err2 instanceof Error ? err2.message : 'Tu sesión venció, entra de nuevo');
          return;
        }
      }
      setError(msg === 'slug en uso' ? 'Ese nombre de tienda ya existe, prueba otro.' : msg);
    } finally {
      setCargando(false);
    }
  }

  return (
    <AdminShell>
      <div className="page-head">
        <div>
          <h2>Crea tu tienda</h2>
          <p className="muted small" style={{ margin: '4px 0 0' }}>
            Empieza tu prueba gratis. Serás el propietario.
          </p>
        </div>
      </div>

      {creada ? (
        <div className="card">
          <h3>¡{creada.nombre} ya existe! 🎉</h3>
          <p className="muted small">
            Tu dirección será <b>{creada.slug}.mitienda.com</b>. Por ahora tu tienda está en
            prueba: actívala para empezar a cobrar.
          </p>
          <div className="btn-row" style={{ maxWidth: 420 }}>
            <Link to="/admin" className="btn btn-primary" style={{ flex: 1, textAlign: 'center' }}>
              Ir a mi panel
            </Link>
            <Link to="/tiendas" className="btn" style={{ flex: 1, textAlign: 'center' }}>
              Ver mis tiendas
            </Link>
          </div>
        </div>
      ) : (
        <div className="card" style={{ maxWidth: 560 }}>
          <form onSubmit={onSubmit}>
            <label className="field">
              <span>¿Cómo se llama tu negocio?</span>
              <input
                required
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Ej. Café Andino"
              />
            </label>
            <label className="field">
              <span>Elige su dirección</span>
              <input
                required
                value={slug}
                onChange={(e) => setSlug(e.target.value)}
                placeholder="Ej. cafe-andino"
              />
              <span className="hint">Solo minúsculas, números y guiones. Así la verán tus clientes.</span>
            </label>
            <label className="field">
              <span>¿Qué vendes? (opcional)</span>
              <input
                value={descripcion}
                onChange={(e) => setDescripcion(e.target.value)}
                placeholder="Ej. Café de especialidad"
              />
            </label>
            <button type="submit" className="btn btn-primary" disabled={cargando}>
              {cargando ? 'Creando…' : 'Crear mi tienda'}
            </button>
          </form>
          {error && <p className="banner banner-error">{error}</p>}
        </div>
      )}
    </AdminShell>
  );
}
