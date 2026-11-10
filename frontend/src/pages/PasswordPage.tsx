import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { confirmarResetPassword, solicitarResetPassword } from '../api/auth';
import AuthLayout from '../components/AuthLayout';

export default function PasswordPage() {
  const [email, setEmail] = useState('');
  const [token, setToken] = useState('');
  const [nueva, setNueva] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);

  async function onSolicitar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await solicitarResetPassword(email);
      setEnviado(true);
      setMsg('Si el correo existe, te enviamos instrucciones. El enlace vale 60 minutos.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos procesar tu solicitud');
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    try {
      await confirmarResetPassword(token, nueva);
      setMsg('Contraseña actualizada. Inicia sesión con la nueva.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ese enlace no es válido o venció');
    }
  }

  return (
    <AuthLayout
      titulo="Recupera tu acceso"
      subtitulo="Te ayudamos a volver a tu tienda en un minuto"
      pie={
        <>
          ¿La recordaste? <Link to="/login">Volver a iniciar sesión</Link>
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
            Enviar instrucciones
          </button>
        </form>
      ) : (
        <form onSubmit={onConfirmar}>
          <label className="field">
            <span>Código del correo</span>
            <input
              required
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Pégalo aquí"
            />
          </label>
          <label className="field">
            <span>Nueva contraseña</span>
            <input
              type="password"
              required
              minLength={8}
              value={nueva}
              onChange={(e) => setNueva(e.target.value)}
              placeholder="Mínimo 8 caracteres"
              autoComplete="new-password"
            />
          </label>
          <button type="submit" className="btn btn-primary">
            Guardar contraseña
          </button>
        </form>
      )}
      {msg && <p className="banner banner-ok">{msg}</p>}
      {error && <p className="banner banner-error">{error}</p>}
    </AuthLayout>
  );
}
