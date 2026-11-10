import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { confirmarResetPassword, solicitarResetPassword } from '../api/auth';

export default function PasswordPage() {
  const [email, setEmail] = useState('');
  const [token, setToken] = useState('');
  const [nueva, setNueva] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSolicitar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await solicitarResetPassword(email);
      setMsg('Solicitud enviada (genérica, no revela si el email existe). Vale 60 min.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falló la solicitud');
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await confirmarResetPassword(token, nueva);
      setMsg('Password fijada. Revoca todas tus sesiones: vuelve a /login.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Token inválido o expirado');
    }
  }

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/login">← Login</Link>
        <span>Password: solicitar + confirmar</span>
      </nav>
      <div className="cols">
        <section className="card">
          <h2>POST /auth/password/solicitar</h2>
          <form onSubmit={onSolicitar}>
            <label>
              Email
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <button type="submit">Solicitar reset</button>
          </form>
        </section>
        <section className="card">
          <h2>POST /auth/password/confirmar</h2>
          <form onSubmit={onConfirmar}>
            <label>
              Token del correo
              <input required value={token} onChange={(e) => setToken(e.target.value)} />
            </label>
            <label>
              Nueva password (mín 8)
              <input
                type="password"
                required
                minLength={8}
                value={nueva}
                onChange={(e) => setNueva(e.target.value)}
              />
            </label>
            <button type="submit">Fijar password</button>
          </form>
        </section>
      </div>
      {msg && <p className="ok">{msg}</p>}
      {error && <p className="error">{error}</p>}
    </div>
  );
}
