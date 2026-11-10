import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const NAV = [
  { to: '/admin', label: 'Inicio', ico: '⌂' },
  { to: '/tiendas/nueva', label: 'Tiendas', ico: '▦' },
  { to: '/totp', label: 'Seguridad', ico: '◈' },
  { to: '/email', label: 'Correo', ico: '✉' },
  { to: '/password', label: 'Contraseña', ico: '⚿' },
];

export default function AdminShell({ children }: { children: ReactNode }) {
  const { email, mfa, salir } = useAuth();
  const nav = useNavigate();

  async function onSalir() {
    await salir();
    nav('/login', { replace: true });
  }

  return (
    <div className="shell">
      <header className="topbar">
        <Link to="/admin" className="logo" style={{ color: 'inherit', textDecoration: 'none' }}>
          <span className="bag">S</span> shopify
        </Link>
        <input className="search" placeholder="Buscar pedidos, productos y clientes" disabled />
        <div className="who">
          {mfa === 'pendiente' ? (
            <span className="badge badge-trial">Prueba</span>
          ) : (
            <span className="badge badge-full">Activa</span>
          )}
          <span className="avatar">{(email ?? '?').slice(0, 1).toUpperCase()}</span>
          <button className="btn" onClick={onSalir}>
            Salir
          </button>
        </div>
      </header>
      <div className="shell-body">
        <aside className="sidebar">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              <span className="ico">{n.ico}</span>
              {n.label}
            </NavLink>
          ))}
          <div className="nav-sep">Cuenta</div>
          <Link to="/registro" className="nav-item">
            <span className="ico">＋</span> Crear usuario
          </Link>
        </aside>
        <main className="content">{children}</main>
      </div>
    </div>
  );
}
