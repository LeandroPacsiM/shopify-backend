import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import {
  confirmarEnrolamientoTotp,
  extraerSecretOtpauth,
  iniciarEnrolamientoTotp,
} from '../api/auth';

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
      const r = await confirmarEnrolamientoTotp(email, password, codigo);
      setBackups(r.backupCodes);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Código inválido');
    } finally {
      setCargando(false);
    }
  }

  return (
    <div className="page">
      <nav className="topnav">
        <Link to="/login">← Login</Link>
        <span>TOTP: iniciar + confirmar</span>
      </nav>
      <div className="cols">
        <section className="card">
          <h2>POST /auth/totp/iniciar</h2>
          <form onSubmit={onIniciar}>
            <label>
              Email
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <label>
              Password
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <button type="submit" disabled={cargando}>
              {cargando ? 'Generando…' : 'Generar QR'}
            </button>
          </form>
          {otpauth && (
            <>
              <p className="muted small">Escanea esto con Google Authenticator:</p>
              <QRCodeSVG value={otpauth} size={200} />
              <p className="muted small">O añade manual (cuenta + secret):</p>
              <p className="mono">{otpauth}</p>
              {secret && (
                <p>
                  Secret: <code>{secret}</code>
                </p>
              )}
            </>
          )}
        </section>
        <section className="card">
          <h2>POST /auth/totp/confirmar</h2>
          <form onSubmit={onConfirmar}>
            <label>
              Código 6 dígitos de la app
              <input
                required
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
                inputMode="numeric"
              />
            </label>
            <button type="submit" disabled={cargando || !otpauth}>
              Confirmar y generar backups
            </button>
          </form>
          {backups.length > 0 && (
            <>
              <p className="ok">2FA habilitado. Guarda estos 10 backup codes:</p>
              <ul className="mono">
                {backups.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            </>
          )}
        </section>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}
