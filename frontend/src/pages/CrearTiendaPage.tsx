import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { crearTienda, refreshSesion } from '../api/auth';
import { useAuth } from '../auth/AuthContext';

export default function CrearTiendaPage() {
  const { accessToken, email, mfa, guardarSesion } = useAuth();
  const [nombre, setNombre] = useState('Mi tienda demo');
  const [slug, setSlug] = useState('mi-tienda-demo');
  const [descripcion, setDescripcion] = useState('');
  const [ok, setOk] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
    setOk(null);
    try {
      const t = await intentar(accessToken as string);
      setOk(`Tienda ${t.id} creada: ${t.nombre} (${t.slug}). Quedaste STORE_OWNER. Usa X-Tienda-Slug: ${t.slug} en /api/admin/*`);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'No se pudo crear';
      if (/401|sesión|token/i.test(msg)) {
        try {
          const r = await refreshSesion();
          guardarSesion(r.accessToken, r.roles, email ?? '');
          const t = await intentar(r.accessToken);
          setOk(`Tienda ${t.id} creada tras refresh: ${t.nombre} (${t.slug}).`);
          return;
        } catch (err2) {
          setError(err2 instanceof Error ? err2.message : 'No se pudo crear');
          return;
        }
      }
      setError(msg);
    }
  }

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/admin">← Admin</Link>
        <span>Crear tienda ({email})</span>
      </nav>
      <section className="card narrow">
        <h2>POST /auth/tiendas</h2>
        <p className="muted">Requiere login. El creador queda STORE_OWNER, suscripción pendiente.</p>
        {mfa === 'pendiente' && (
          <p className="muted">Vas con sesión trial (mfa=pendiente): crear tienda está permitido.</p>
        )}
        <form onSubmit={onSubmit}>
          <label>
            Nombre
            <input required value={nombre} onChange={(e) => setNombre(e.target.value)} />
          </label>
          <label>
            Slug [a-z0-9-]
            <input required value={slug} onChange={(e) => setSlug(e.target.value)} />
          </label>
          <label>
            Descripción (opcional)
            <input value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
          </label>
          <button type="submit">Crear tienda</button>
        </form>
        {ok && <p className="ok">{ok}</p>}
        {error && <p className="error">{error}</p>}
      </section>
    </div>
  );
}
