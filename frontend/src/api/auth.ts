export interface ChallengeResponse {
  requiereTotp: boolean;
}

export interface TokensResponse {
  accessToken: string;
  roles: string[];
}

export interface RegistroResponse {
  usuarioId: number;
  email: string;
  requiereEnrolamientoTotp: boolean;
}

export interface TotpConfirmadoResponse {
  habilitado: boolean;
  backupCodes: string[];
}

export interface TiendaCreadaResponse {
  id: number;
  nombre: string;
  slug: string;
  activa: boolean;
}

async function leerError(res: Response): Promise<string> {
  try {
    const data = await res.json();
    if (typeof data === 'string') return data;
    if (data?.mensaje) return String(data.mensaje);
    if (data?.detail) return String(data.detail);
    if (data?.message) return String(data.message);
    if (data?.title) return String(data.title);
    return `HTTP ${res.status}`;
  } catch {
    return `HTTP ${res.status}`;
  }
}

export async function loginPaso1(email: string, password: string): Promise<ChallengeResponse> {
  const res = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email: email.trim(), password }),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as ChallengeResponse;
}

export async function loginPaso2(
  email: string,
  password: string,
  opts: { codigo?: string; backupCode?: string },
): Promise<TokensResponse> {
  const res = await fetch('/auth/login/totp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email: email.trim(), password, ...opts }),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as TokensResponse;
}

export async function refreshSesion(): Promise<TokensResponse> {
  const res = await fetch('/auth/refresh', {
    method: 'POST',
    credentials: 'include',
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as TokensResponse;
}

export async function logout(): Promise<void> {
  await fetch('/auth/logout', { method: 'POST', credentials: 'include' });
}

async function postSinRespuesta(url: string, body: unknown): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await leerError(res));
}

export async function registroAdministrativo(input: {
  email: string;
  password: string;
  nombre?: string;
  apellido?: string;
}): Promise<RegistroResponse> {
  const res = await fetch('/auth/registro-administrativo', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({
      email: input.email.trim(),
      password: input.password,
      nombre: input.nombre?.trim() || undefined,
      apellido: input.apellido?.trim() || undefined,
    }),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as RegistroResponse;
}

export const solicitarVerificacionEmail = (email: string) =>
  postSinRespuesta('/auth/email/solicitar', { email: email.trim() });

export const confirmarVerificacionEmail = (token: string) =>
  postSinRespuesta('/auth/email/confirmar', { token: token.trim() });

export const solicitarResetPassword = (email: string) =>
  postSinRespuesta('/auth/password/solicitar', { email: email.trim() });

export const confirmarResetPassword = (token: string, password: string) =>
  postSinRespuesta('/auth/password/confirmar', { token: token.trim(), password });

export async function iniciarEnrolamientoTotp(email: string, password: string): Promise<string> {
  const res = await fetch('/auth/totp/iniciar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email: email.trim(), password }),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.text()).trim();
}

export async function confirmarEnrolamientoTotp(
  email: string,
  password: string,
  codigo: string,
): Promise<TotpConfirmadoResponse> {
  const res = await fetch('/auth/totp/confirmar', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ email: email.trim(), password, codigo: codigo.trim() }),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as TotpConfirmadoResponse;
}

export async function crearTienda(
  accessToken: string,
  input: { nombre: string; slug: string; descripcion?: string },
): Promise<TiendaCreadaResponse> {
  const res = await fetch('/auth/tiendas', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    credentials: 'include',
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as TiendaCreadaResponse;
}

export function extraerSecretOtpauth(url: string): string {
  const m = url.match(/[?&]secret=([A-Z2-7]+)/i);
  return m ? m[1].toUpperCase() : '';
}
