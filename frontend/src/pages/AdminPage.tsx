import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import AdminShell from '../components/AdminShell';

export default function AdminPage() {
  const { accessToken, roles, email, mfa, salir, refrescar } = useAuth();
  const nav = useNavigate();
  const [msg, setMsg] = useState<string | null>(null);

  if (!accessToken) return <Navigate to="/login" replace />;

  async function onRefresh() {
    setMsg(null);
    const ok = await refrescar();
    setMsg(ok ? 'Sesión renovada correctamente.' : 'La sesión caducó, vuelve a iniciar sesión.');
  }

  async function onSalir() {
    await salir();
    nav('/login', { replace: true });
  }

  return (
    <AdminShell>
      <div className="page-head">
        <div>
          <h2>Hola de nuevo 👋</h2>
          <p className="muted small" style={{ margin: '4px 0 0' }}>
            Esto es lo que tu tienda necesita para despegar.
          </p>
        </div>
        {mfa === 'pendiente' ? (
          <span className="badge badge-trial">Tienda de prueba</span>
        ) : (
          <span className="badge badge-full">Tienda activa</span>
        )}
      </div>

      {mfa === 'pendiente' && (
        <div className="card">
          <h3>Te falta un paso para vender</h3>
          <p className="muted small" style={{ margin: '0 0 12px' }}>
            Activa la verificación en dos pasos y tu tienda quedará lista para cobrar.
          </p>
          <Link to="/totp" className="btn btn-primary" style={{ width: 'auto' }}>
            Activar verificación
          </Link>
        </div>
      )}

      <div className="grid-3">
        <div className="stat">
          <h3>Tu correo</h3>
          <p>{email}</p>
        </div>
        <div className="stat">
          <h3>Tu rol</h3>
          <p>
            {roles.length
              ? roles.join(', ')
              : 'Propietario (los permisos viven en tu tienda)'}
          </p>
        </div>
        <div className="stat">
          <h3>Estado de sesión</h3>
          <p>{mfa === 'pendiente' ? 'Prueba — solo lectura' : 'Completa — acceso total'}</p>
        </div>
      </div>

      <div className="card">
        <h3>Mantenimiento de sesión</h3>
        <div className="btn-row" style={{ maxWidth: 420 }}>
          <button className="btn" onClick={onRefresh}>
            Renovar sesión
          </button>
          <button className="btn" onClick={onSalir}>
            Cerrar sesión
          </button>
        </div>
        {msg && <p className="banner banner-ok">{msg}</p>}
      </div>
    </AdminShell>
  );
}
