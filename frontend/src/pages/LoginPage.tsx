import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { loginPaso1, loginPaso2, loginPaso2Trial } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';

export default function LoginPage() {
  const nav = useNavigate();
  const { guardarSesion } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [codigo, setCodigo] = useState('');
  const [usaBackup, setUsaBackup] = useState(false);
  const [paso, setPaso] = useState<1 | 2>(1);
  const [enrolado, setEnrolado] = useState(true);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onPaso1(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const ch = await loginPaso1(email, password);
      setEnrolado(ch.totpEnrolado);
      setPaso(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos verificar tus datos');
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
      setError(err instanceof Error ? err.message : 'El código no coincide, intenta de nuevo');
    } finally {
      setCargando(false);
    }
  }

  async function onTrial() {
    setError(null);
    setCargando(true);
    try {
      const tokens = await loginPaso2Trial(email, password);
      guardarSesion(tokens.accessToken, tokens.roles, email.trim().toLowerCase());
      nav('/tiendas/nueva', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos iniciar la prueba');
    } finally {
      setCargando(false);
    }
  }

  return (
    <AuthLayout
      titulo={paso === 1 ? 'Inicia sesión' : enrolado ? 'Confirma que eres tú' : 'Empieza tu prueba'}
      subtitulo={
        paso === 1
          ? 'Accede a tu panel para gestionar tu tienda'
          : enrolado
            ? 'Ábrelo en tu app de autenticación'
            : 'Aún no activas la verificación en dos pasos. Entra, crea tu tienda y actívala cuando quieras.'
      }
      pie={
        <>
          ¿Aún no vendes? <Link to="/registro">Crea tu cuenta gratis</Link>
          {' · '}
          <Link to="/password">Olvidé mi contraseña</Link>
        </>
      }
    >
      {paso === 1 ? (
        <form onSubmit={onPaso1}>
          <label className="field">
            <span>Correo electrónico</span>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tucorreo@ejemplo.com"
              autoComplete="username"
            />
          </label>
          <label className="field">
            <span>Contraseña</span>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando}>
            {cargando ? 'Verificando…' : 'Continuar'}
          </button>
        </form>
      ) : enrolado ? (
        <form onSubmit={onPaso2}>
          <label className="field">
            <span>{usaBackup ? 'Código de respaldo' : 'Código de 6 dígitos'}</span>
            <input
              required
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              placeholder={usaBackup ? 'Ej. PZEXGNH4' : 'Ej. 123456'}
              inputMode={usaBackup ? 'text' : 'numeric'}
              autoComplete="one-time-code"
            />
            <span className="hint">Ábrelo en tu app de autenticación.</span>
          </label>
          <label className="field check">
            <input
              type="checkbox"
              checked={usaBackup}
              onChange={(e) => {
                setUsaBackup(e.target.checked);
                setCodigo('');
              }}
            />
            Usar un código de respaldo
          </label>
          <div className="btn-row">
            <button type="button" className="btn" disabled={cargando} onClick={() => setPaso(1)}>
              Atrás
            </button>
            <button type="submit" className="btn btn-primary" disabled={cargando}>
              {cargando ? 'Entrando…' : 'Entrar a mi tienda'}
            </button>
          </div>
          <button
            type="button"
            className="btn btn-block"
            style={{ marginTop: 10 }}
            disabled={cargando}
            onClick={onTrial}
          >
            Seguir sin verificación (activarla después)
          </button>
        </form>
      ) : (
        <div>
          <p className="muted small" style={{ marginTop: 0 }}>
            Entrarás en modo prueba: podrás crear tu tienda y mirarla, pero para cobrar
            primero activa la verificación desde tu panel.
          </p>
          <button
            type="button"
            className="btn btn-primary btn-block"
            disabled={cargando}
            onClick={onTrial}
          >
            {cargando ? 'Entrando…' : 'Seguir sin verificación (activarla después)'}
          </button>
          <button
            type="button"
            className="btn btn-block"
            style={{ marginTop: 10 }}
            disabled={cargando}
            onClick={() => setPaso(1)}
          >
            Atrás
          </button>
        </div>
      )}
      {error && <p className="banner banner-error">{error}</p>}
    </AuthLayout>
  );
}
