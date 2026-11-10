import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import {
  confirmarEnrolamientoTotp,
  extraerSecretOtpauth,
  iniciarEnrolamientoTotp,
} from '../api/auth';
import AuthLayout from '../components/AuthLayout';

export default function TotpPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otpauth, setOtpauth] = useState('');
  const [codigo, setCodigo] = useState('');
  const [backups, setBackups] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);

  const secret = otpauth ? extraerSecretOtpauth(otpauth) : '';

  async function onIniciar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBackups([]);
    setCargando(true);
    try {
      const url = await iniciarEnrolamientoTotp(email, password);
      setOtpauth(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No pudimos generar tu código QR');
    } finally {
      setCargando(false);
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const r = await confirmarEnrolamientoTotp(email, password, codigo);
      setBackups(r.backupCodes);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ese código no coincide, intenta de nuevo');
    } finally {
      setCargando(false);
    }
  }

  return (
    <AuthLayout
      titulo="Protege tu cuenta"
      subtitulo="Activa la verificación en dos pasos con tu celular"
      pie={
        <>
          ¿Ya la activaste? <Link to="/login">Entrar a mi tienda</Link>
        </>
      }
    >
      {!otpauth ? (
        <form onSubmit={onIniciar}>
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
          <label className="field">
            <span>Contraseña</span>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando}>
            {cargando ? 'Generando…' : 'Mostrar mi código QR'}
          </button>
        </form>
      ) : backups.length === 0 ? (
        <form onSubmit={onConfirmar}>
          <p className="muted small" style={{ marginTop: 0 }}>
            1. Escanea este QR con Google Authenticator o similar.
          </p>
          <div className="qr-box">
            <QRCodeSVG value={otpauth} size={190} />
          </div>
          <label className="field" style={{ marginTop: 14 }}>
            <span>2. Escribe el código de 6 dígitos que ves en la app</span>
            <input
              required
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              placeholder="Ej. 123456"
              inputMode="numeric"
              autoComplete="one-time-code"
            />
          </label>
          {secret && (
            <p className="muted small">
              ¿Sin cámara? Añade manual esta clave: <code>{secret}</code>
            </p>
          )}
          <button type="submit" className="btn btn-primary" disabled={cargando}>
            {cargando ? 'Verificando…' : 'Activar protección'}
          </button>
        </form>
      ) : (
        <>
          <p className="banner banner-ok">¡Cuenta protegida! 🎉 Guarda estos códigos de respaldo.</p>
          <ul className="mono backup-grid">
            {backups.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
          <Link to="/login" className="btn btn-primary btn-block" style={{ marginTop: 14, textAlign: 'center' }}>
            Entrar a mi tienda
          </Link>
        </>
      )}
      {error && <p className="banner banner-error">{error}</p>}
    </AuthLayout>
  );
}
