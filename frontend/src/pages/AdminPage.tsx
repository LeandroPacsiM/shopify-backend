import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function AdminPage() {
  const { accessToken, roles, email, salir, refrescar } = useAuth();
  const nav = useNavigate();
  const [msg, setMsg] = useState<string | null>(null);

  if (!accessToken) return <Navigate to="/login" replace />;

  async function onRefresh() {
    setMsg(null);
    const ok = await refrescar();
    setMsg(ok ? 'Refresh rotado OK (cookie renovada)' : 'Refresh falló: vuelve a /login');
  }

  async function onSalir() {
    await salir();
    nav('/login', { replace: true });
  }

  return (
    <div className="admin-root">
      <header className="admin-topbar">
        <span className="logo">shopify admin</span>
        <input className="search" placeholder="Search (mock visual)" disabled />
        <span className="user">{email ?? 'staff'}</span>
        <button onClick={onSalir}>Salir</button>
      </header>

      <div className="admin-body">
        <aside className="sidebar">
          <span>Home</span>
          <span>Orders</span>
          <span>Products</span>
          <span>Customers</span>
          <span>Settings</span>
          <em>Solo shell visual. Usuarios/roles lo lleva el otro agente.</em>
        </aside>

        <main className="admin-main">
          <h2>Dashboard (prueba de backend)</h2>
          <p className="muted">
            To get orders and accept payments you need a plan — texto del Figma SaaS replicado
            como referencia visual.
          </p>

          <div className="grid">
            <div className="stat">
              <h3>Email</h3>
              <p>{email}</p>
            </div>
            <div className="stat">
              <h3>Roles (JWT)</h3>
              <p>{roles.length ? roles.join(', ') : '(vacío: staff sin rol global, rol va en tienda_usuarios)'}</p>
            </div>
            <div className="stat">
              <h3>Access token</h3>
              <p className="mono">{accessToken.slice(0, 32)}…</p>
            </div>
          </div>

          <div className="row">
            <button onClick={onRefresh}>Probar /auth/refresh</button>
            <Link to="/tiendas/nueva">Crear tienda</Link>
          </div>
          {msg && <p className="ok">{msg}</p>}
          <p className="muted small">
            Flujos sin Postman: <Link to="/registro">registro</Link> ·{' '}
            <Link to="/email">email</Link> · <Link to="/totp">TOTP</Link> ·{' '}
            <Link to="/password">password</Link> · <Link to="/tiendas/nueva">crear tienda</Link>
          </p>
        </main>
      </div>
    </div>
  );
}
