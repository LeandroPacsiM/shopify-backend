import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

const PASOS = [
  { titulo: 'Crea tu cuenta', texto: 'Registro staff en segundos, sin tarjeta.' },
  { titulo: 'Activa tu tienda', texto: 'Tu tienda nace con prueba y tú como propietario.' },
  { titulo: 'Protege el acceso', texto: 'Verificación en dos pasos antes de vender.' },
];

export default function AuthLayout({
  titulo,
  subtitulo,
  children,
  pie,
}: {
  titulo: string;
  subtitulo: string;
  children: ReactNode;
  pie?: ReactNode;
}) {
  return (
    <div className="auth-split">
      <aside className="auth-brand">
        <Link to="/login" className="logo" style={{ color: '#fff', textDecoration: 'none' }}>
          <span className="bag">S</span> shopify
        </Link>
        <div>
          <h1>Making Commerce Better for Everyone</h1>
          <p>La plataforma para vender donde sea. Empieza con tu tienda de prueba.</p>
        </div>
        <ul>
          {PASOS.map((p, i) => (
            <li key={p.titulo}>
              <span className="auth-dot">{i + 1}</span>
              <span>
                <b>{p.titulo}. </b>
                {p.texto}
              </span>
            </li>
          ))}
        </ul>
      </aside>
      <main className="auth-panel">
        <section className="auth-card">
          <h2>{titulo}</h2>
          <p className="sub">{subtitulo}</p>
          {children}
          {pie && <div className="auth-links">{pie}</div>}
        </section>
      </main>
    </div>
  );
}
