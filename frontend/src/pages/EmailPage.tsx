import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  confirmarVerificacionEmail,
  loginPaso2Trial,
  solicitarVerificacionEmail,
} from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import AuthLayout from '../components/AuthLayout';

interface EstadoLlegada {
  email?: string;
  password?: string;
}

export default function EmailPage() {
  const nav = useNavigate();
  const { guardarSesion } = useAuth();
  const llegada = (useLocation().state ?? {}) as EstadoLlegada;
  const [email, setEmail] = useState(llegada.email ?? '');
  // Solo memoria, viene del registro: permite entrar directo tras el código.
  const [password] = useState(llegada.password ?? '');
  const [token, setToken] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Del registro el código ya va en camino: directo al código.
  const [enviado, setEnviado] = useState(!!llegada.email);
  const [entrando, setEntrando] = useState(false);

  async function onSolicitar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await solicitarVerificacionEmail(email);
      setEnviado(true);
      setMsg('Si el correo existe, ya va en camino. Revisa también spam.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos enviar el correo');
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await confirmarVerificacionEmail(token);
      if (!password) {
        setMsg('¡Correo activado! Ahora entra desde el login.');
        return;
      }
      // Del registro: entra directo en modo trial (JWT mfa=pendiente).
      setEntrando(true);
      try {
        const tokens = await loginPaso2Trial(email, password);
        guardarSesion(tokens.accessToken, tokens.roles, email.trim().toLowerCase());
        nav('/tiendas/nueva', { replace: true });
      } finally {
        setEntrando(false);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ese código no es válido o venció');
    }
  }

  return (
    <AuthLayout
      titulo={llegada.email ? 'Ingresa tu código de verificación' : 'Activa tu correo'}
      subtitulo={
        llegada.email
          ? `Te lo enviamos a ${email}. Revisa también spam.`
          : 'Te enviamos un código para confirmar que eres tú'
      }
      pie={
        <>
          ¿Ya lo activaste? <Link to="/login">Inicia sesión</Link>
        </>
      }
    >
      {!enviado ? (
        <form onSubmit={onSolicitar}>
          <label className="field">
            <span>Correo electrónico</span>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tucorreo@ejemplo.com"
            />
          </label>
          <button type="submit" className="btn btn-primary">
            Enviar código
          </button>
        </form>
      ) : (
        <form onSubmit={onConfirmar}>
          <p className="muted small" style={{ marginTop: 0 }}>
            Código enviado a <b>{email}</b>
          </p>
          <label className="field">
            <span>Código de activación</span>
            <input
              required
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Pégalo aquí"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={entrando}>
            {entrando ? 'Entrando…' : 'Activar mi cuenta'}
          </button>
        </form>
      )}
      {msg && <p className="banner banner-ok">{msg}</p>}
      {error && <p className="banner banner-error">{error}</p>}
    </AuthLayout>
  );
}
