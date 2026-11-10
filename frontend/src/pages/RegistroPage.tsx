import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { registroAdministrativo } from '../api/auth';

export default function RegistroPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [ok, setOk] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(null);
    setCargando(true);
    try {
      const r = await registroAdministrativo({ email, password, nombre, apellido });
      setOk(`Usuario ${r.usuarioId} creado (${r.email}). Siguiente: verifica tu email en /email y enrola TOTP en /totp.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo registrar');
    } finally {
      setCargando(false);
    }
  }

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/login">← Login</Link>
        <span>Registro staff (sin permisos)</span>
      </nav>
      <section className="card narrow">
        <h2>POST /auth/registro-administrativo</h2>
        <p className="muted">Crea identidad staff sin tienda ni rol. No crea CUSTOMER.</p>
        <form onSubmit={onSubmit}>
          <label>
            Email
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </label>
          <label>
            Password (mín 8)
            <input
              type="password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <label>
            Nombre (opcional)
            <input value={nombre} onChange={(e) => setNombre(e.target.value)} />
          </label>
          <label>
            Apellido (opcional)
            <input value={apellido} onChange={(e) => setApellido(e.target.value)} />
          </label>
          <button type="submit" disabled={cargando}>
            {cargando ? 'Registrando…' : 'Registrar'}
          </button>
        </form>
        {ok && <p className="ok">{ok}</p>}
        {error && <p className="error">{error}</p>}
      </section>
    </div>
  );
}
