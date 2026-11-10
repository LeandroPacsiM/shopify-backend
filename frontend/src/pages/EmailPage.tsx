import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { confirmarVerificacionEmail, solicitarVerificacionEmail } from '../api/auth';

export default function EmailPage() {
  const [email, setEmail] = useState('');
  const [token, setToken] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSolicitar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await solicitarVerificacionEmail(email);
      setMsg('Solicitud enviada (respuesta genérica). En local el código va por log; en tu demo pide el UPDATE por SQL.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falló la solicitud');
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await confirmarVerificacionEmail(token);
      setMsg('Email verificado. Ya puedes /totp y /login.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Token inválido o expirado');
    }
  }

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/login">← Login</Link>
        <span>Email: solicitar + confirmar</span>
      </nav>
      <div className="cols">
        <section className="card">
          <h2>POST /auth/email/solicitar</h2>
          <form onSubmit={onSolicitar}>
            <label>
              Email
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <button type="submit">Reenviar código</button>
          </form>
        </section>
        <section className="card">
          <h2>POST /auth/email/confirmar</h2>
          <form onSubmit={onConfirmar}>
            <label>
              Token del correo
              <input required value={token} onChange={(e) => setToken(e.target.value)} />
            </label>
            <button type="submit">Verificar email</button>
          </form>
        </section>
      </div>
      {msg && <p className="ok">{msg}</p>}
      {error && <p className="error">{error}</p>}
    </div>
  );
}
