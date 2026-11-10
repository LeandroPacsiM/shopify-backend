/* Demo Auth — JS vanilla, sin dependencias. JWTs solo en memoria. */
'use strict';

const $ = (id) => document.getElementById(id);
const base = () => $('baseUrl').value.replace(/\/$/, '');

let jwtStaff = null; // nunca a localStorage
let jwtCustomer = null;

function log(cual, ...partes) {
  const el = cual === 'staff' ? $('log-staff') : $('log-customer');
  el.textContent += partes.join(' ') + '\n';
  el.scrollTop = el.scrollHeight;
}

function muestra(cual, titulo, obj) {
  log(cual, '—— ' + titulo + ' ——');
  log(cual, typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2));
}

async function post(cual, ruta, cuerpo, { auth = null, credenciales = false } = {}) {
  const cabeceras = { 'Content-Type': 'application/json' };
  if (auth) cabeceras['Authorization'] = 'Bearer ' + auth;
  const opciones = {
    method: 'POST',
    headers: cabeceras,
    credentials: credenciales ? 'include' : 'same-origin',
  };
  if (cuerpo !== undefined) opciones.body = JSON.stringify(cuerpo);
  log(cual, '→ POST ' + ruta, cuerpo === undefined ? '' : JSON.stringify(cuerpo));
  let respuesta;
  try {
    respuesta = await fetch(base() + ruta, opciones);
  } catch (e) {
    muestra(cual, 'ERROR de red (¿backend levantado con perfil local?)', String(e));
    return null;
  }
  const texto = await respuesta.text();
  let json = null;
  try { json = texto ? JSON.parse(texto) : null; } catch { /* 204 o texto plano */ }
  // /totp/iniciar devuelve string JSON ("otpauth://..."): JSON.parse lo deja en texto útil
  muestra(cual, '← ' + respuesta.status, json !== null ? json : (texto || '(vacío)'));
  if (!respuesta.ok) return null;
  return json !== null ? json : texto;
}

const val = (id) => $(id).value.trim();
const staff = () => ({ email: val('s-email'), password: val('s-pass') });

const acciones = {
  // ---- STAFF ----
  'registro': async () => {
    await post('staff', '/auth/registro-administrativo', {
      email: val('s-email'), password: val('s-pass'),
      nombre: val('s-nombre') || undefined, apellido: undefined,
    });
  },
  'email-solicitar': async () => {
    await post('staff', '/auth/email/solicitar', { email: val('s-email') });
  },
  'email-confirmar': async () => {
    await post('staff', '/auth/email/confirmar', { token: val('s-etoken') });
  },
  'totp-iniciar': async () => {
    const url = await post('staff', '/auth/totp/iniciar', staff());
    if (typeof url === 'string') {
      const m = url.match(/[?&]secret=([A-Z2-7]+)/i);
      $('s-secreto').value = m ? m[1] : '';
      log('staff', 'URL otpauth (pégala en tu app autenticadora):', url);
      if (!m) log('staff', 'No se encontró secret= en la URL; entra el código igual.');
    }
  },
  'totp-confirmar': async () => {
    await post('staff', '/auth/totp/confirmar',
      { ...staff(), codigo: val('s-codigo1') });
  },
  'login': async () => {
    await post('staff', '/auth/login', staff());
  },
  'login-totp': async () => {
    const esBackup = $('s-es-backup').checked;
    const codigo = val('s-codigo2');
    const cuerpo = esBackup
      ? { ...staff(), backupCode: codigo }
      : { ...staff(), codigo };
    const json = await post('staff', '/auth/login/totp', cuerpo, { credenciales: true });
    if (json && json.accessToken) {
      jwtStaff = json.accessToken;
      log('staff', 'JWT guardado en memoria. Roles:', JSON.stringify(json.roles));
    }
  },
  'crear-tienda': async () => {
    if (!jwtStaff) { log('staff', 'Primero entra (paso 5): no hay JWT en memoria.'); return; }
    await post('staff', '/auth/tiendas',
      { nombre: val('s-tienda'), slug: val('s-slug') }, { auth: jwtStaff });
  },
  'refresh': async () => {
    const json = await post('staff', '/auth/refresh', undefined, { credenciales: true });
    if (json && json.accessToken) {
      jwtStaff = json.accessToken;
      log('staff', 'JWT renovado en memoria.');
    }
  },
  'logout': async () => {
    await post('staff', '/auth/logout', undefined, { credenciales: true });
    jwtStaff = null;
    log('staff', 'JWT olvidado (memoria limpia).');
  },
  // ---- CUSTOMER ----
  'c-solicitar': async () => {
    await post('customer', '/auth/cliente/solicitar',
      { tiendaSlug: val('c-slug'), email: val('c-email') });
  },
  'c-verificar': async () => {
    const json = await post('customer', '/auth/cliente/verificar', {
      tiendaSlug: val('c-slug'), email: val('c-email'), codigo: val('c-codigo'),
    }, { credenciales: true });
    if (json && json.accessToken) {
      jwtCustomer = json.accessToken;
      log('customer', 'JWT CUSTOMER en memoria. clienteId:', String(json.clienteId));
    }
  },
  'c-refresh': async () => {
    const json = await post('customer', '/auth/cliente/refresh', undefined, { credenciales: true });
    if (json && json.accessToken) {
      jwtCustomer = json.accessToken;
      log('customer', 'JWT CUSTOMER renovado en memoria.');
    }
  },
  'c-logout': async () => {
    await post('customer', '/auth/cliente/logout', undefined, { credenciales: true });
    jwtCustomer = null;
    log('customer', 'JWT olvidado (memoria limpia).');
  },
};

document.querySelectorAll('button[data-action]').forEach((b) => {
  b.addEventListener('click', () => acciones[b.dataset.action]());
});
