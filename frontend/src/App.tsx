import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import AdminPage from './pages/AdminPage';
import CrearTiendaPage from './pages/CrearTiendaPage';
import EmailPage from './pages/EmailPage';
import LoginPage from './pages/LoginPage';
import PasswordPage from './pages/PasswordPage';
import RegistroPage from './pages/RegistroPage';
import TotpPage from './pages/TotpPage';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/registro" element={<RegistroPage />} />
          <Route path="/email" element={<EmailPage />} />
          <Route path="/password" element={<PasswordPage />} />
          <Route path="/totp" element={<TotpPage />} />
          <Route path="/tiendas/nueva" element={<CrearTiendaPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
