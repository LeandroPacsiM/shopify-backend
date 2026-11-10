import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { registroAdministrativo } from '../api/auth';
import AuthLayout from '../components/AuthLayout';

export default function RegistroPage() {
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      await registroAdministrativo({ email, password, nombre, apellido });
      // Al código directo: el correo de verificación ya va en camino.
      // Password solo en memoria (router state), nunca en URL ni storage.
      nav('/email', {
        state: { email: email.trim().toLowerCase(), password },
        replace: true,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos crear tu cuenta');
    } finally {
      setCargando(false);
    }
  }

  return (
    <AuthLayout
      titulo="Crea tu cuenta"
      subtitulo="Gratis por 3 días. Sin tarjeta, cancela cuando quieras."
      pie={
        <>
          ¿Ya vendes con nosotros? <Link to="/login">Inicia sesión</Link>
        </>
      }
    >
      <form onSubmit={onSubmit}>
          <div className="btn-row">
            <label className="field" style={{ flex: 1 }}>
              <span>Nombre</span>
              <input
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Ana"
                autoComplete="given-name"
              />
            </label>
            <label className="field" style={{ flex: 1 }}>
              <span>Apellido</span>
              <input
                value={apellido}
                onChange={(e) => setApellido(e.target.value)}
                placeholder="Pérez"
                autoComplete="family-name"
              />
            </label>
          </div>
          <label className="field">
            <span>Correo electrónico</span>
            <input
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tucorreo@ejemplo.com"
              autoComplete="email"
            />
          </label>
          <label className="field">
            <span>Contraseña</span>
            <input
              type="password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Mínimo 8 caracteres"
              autoComplete="new-password"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando}>
            {cargando ? 'Creando…' : 'Crear cuenta gratis'}
          </button>
        </form>
      {error && <p className="banner banner-error">{error}</p>}
    </AuthLayout>
  );
}
