import { useState } from 'react';
import type { FormEvent } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import {
  confirmarEnrolamientoTotp,
  extraerSecretOtpauth,
  iniciarEnrolamientoTotp,
  loginPaso2,
} from '../api/auth';
import { useAuth } from '../auth/AuthContext';

/**
 * Enrolamiento 2FA inline (sin navegar): QR → confirmar → backup codes →
 * entrar con código actual para subir a sesión completa. Password solo en
 * memoria del componente.
 */
export default function EnrolarTotp({ email }: { email: string }) {
  const { guardarSesion } = useAuth();
  const [password, setPassword] = useState('');
  const [otpauth, setOtpauth] = useState('');
  const [codigo, setCodigo] = useState('');
  const [backups, setBackups] = useState<string[]>([]);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const secret = otpauth ? extraerSecretOtpauth(otpauth) : '';

  async function onIniciar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      setOtpauth(await iniciarEnrolamientoTotp(email, password));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar');
    } finally {
      setCargando(false);
    }
  }

  async function onConfirmar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const r = await confirmarEnrolamientoTotp(email, password, codigo.trim());
      setBackups(r.backupCodes);
      setCodigo('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Código inválido');
    } finally {
      setCargando(false);
    }
  }

  async function onCompletar(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setCargando(true);
    try {
      const tokens = await loginPaso2(email, password, { codigo: codigo.trim() });
      guardarSesion(tokens.accessToken, tokens.roles, email);
      setPassword('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Código inválido, revisa tu app');
    } finally {
      setCargando(false);
    }
  }

  return (
    <div>
      {!otpauth ? (
        <form onSubmit={onIniciar}>
          <label className="field">
            <span>Confirma tu password para mostrar el QR</span>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete="current-password"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando} style={{ width: 'auto' }}>
            {cargando ? 'Generando…' : 'Mostrar QR'}
          </button>
        </form>
      ) : backups.length === 0 ? (
        <form onSubmit={onConfirmar}>
          <p className="muted small">Escanea con tu app autenticadora (o mete el secreto a mano):</p>
          <QRCodeSVG value={otpauth} size={160} />
          {secret && <p className="mono small">Secreto: {secret}</p>}
          <label className="field">
            <span>Primer código (6 dígitos)</span>
            <input
              required
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              placeholder="123456"
              inputMode="numeric"
              autoComplete="one-time-code"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando} style={{ width: 'auto' }}>
            {cargando ? 'Confirmando…' : 'Confirmar'}
          </button>
        </form>
      ) : (
        <form onSubmit={onCompletar}>
          <p className="banner banner-ok">2FA activado. Guarda estos backup codes (no se muestran de nuevo):</p>
          <ul className="mono small">
            {backups.map((b) => (
              <li key={b}>{b}</li>
            ))}
          </ul>
          <label className="field">
            <span>Último paso: mete el código actual de tu app para entrar completo</span>
            <input
              required
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              placeholder="123456"
              inputMode="numeric"
              autoComplete="one-time-code"
            />
          </label>
          <button type="submit" className="btn btn-primary" disabled={cargando} style={{ width: 'auto' }}>
            {cargando ? 'Entrando…' : 'Completar y entrar'}
          </button>
        </form>
      )}
      {error && <p className="banner banner-error">{error}</p>}
    </div>
  );
}
