import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginPaso1, loginPaso2 } from '../api/auth';
import { useAuth } from '../auth/AuthContext';

export default function LoginPage() {
  const nav = useNavigate();
  const { guardarSesion } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [codigo, setCodigo] = useState('');
  const [usaBackup, setUsaBackup] = useState(false);
  const [paso, setPaso] = useState<1 | 2>(1);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onPaso1(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const ch = await loginPaso1(email, password);
      if (ch.requiereTotp) setPaso(2);
      else setPaso(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión');
    } finally {
      setCargando(false);
    }
  }

  async function onPaso2(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const tokens = usaBackup
        ? await loginPaso2(email, password, { backupCode: codigo.trim() })
        : await loginPaso2(email, password, { codigo: codigo.trim() });
      guardarSesion(tokens.accessToken, tokens.roles, email.trim().toLowerCase());
      nav('/admin', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Código inválido');
    } finally {
      setCargando(false);
    }
  }

  return (
    <div className="login-root">
      <header className="login-topbar">
        <span className="logo">shopify</span>
        <nav className="links">
          <span>Solutions</span>
          <span>Pricing</span>
          <span>Resources</span>
          <span>What&apos;s new</span>
        </nav>
        <span className="login-hint">Log in</span>
      </header>

      <main className="login-hero">
        <div className="hero-copy">
          <h1>Making Commerce Better for Everyone</h1>
          <p>Solo login staff para probar tu backend. Sin landing completa.</p>
        </div>

        <section className="card">
          <h2>{paso === 1 ? 'Log in staff' : 'Verifica tu 2FA'}</h2>
          <p className="muted">
            {paso === 1
              ? 'Paso 1: email + password → POST /auth/login'
              : 'Paso 2: código TOTP 6 dígitos → POST /auth/login/totp'}
          </p>

          {paso === 1 ? (
            <form onSubmit={onPaso1}>
              <label>
                Email
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="owner@tienda.test"
                  autoComplete="username"
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                />
              </label>
              <button type="submit" disabled={cargando}>
                {cargando ? 'Verificando…' : 'Continuar'}
              </button>
            </form>
          ) : (
            <form onSubmit={onPaso2}>
              <p className="muted small">{email}</p>
              <label>
                {usaBackup ? 'Backup code (8)' : 'Código TOTP (6 dígitos)'}
                <input
                  required
                  value={codigo}
                  onChange={(e) => setCodigo(e.target.value)}
                  placeholder={usaBackup ? 'XXXXXXXX' : '123456'}
                  inputMode={usaBackup ? 'text' : 'numeric'}
                  autoComplete="one-time-code"
                />
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={usaBackup}
                  onChange={(e) => {
                    setUsaBackup(e.target.checked);
                    setCodigo('');
                  }}
                />
                Usar backup code
              </label>
              <div className="row">
                <button type="button" disabled={cargando} onClick={() => setPaso(1)}>
                  Atrás
                </button>
                <button type="submit" disabled={cargando}>
                  {cargando ? 'Entrando…' : 'Entrar'}
                </button>
              </div>
            </form>
          )}

          {error && <p className="error">{error}</p>}
          <p className="muted small">
            Flujo sin Postman: <Link to="/registro">registro</Link> → <Link to="/email">email</Link>{' '}
            → <Link to="/totp">TOTP</Link> → login → <Link to="/tiendas/nueva">crear tienda</Link>.{' '}
            <Link to="/password">Olvidé mi password</Link>.
          </p>
        </section>
      </main>
    </div>
  );
}
