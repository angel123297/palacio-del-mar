import { Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext.jsx';
import { BookingCartProvider } from './context/BookingCartContext.jsx';
import { ToastProvider } from './context/ToastContext.jsx';
import AuthModals from './components/AuthModals.jsx';
import HomePage from './pages/HomePage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import BookingDetailPage from './pages/BookingDetailPage.jsx';
import SuiteDetailPage from './pages/SuiteDetailPage.jsx';
import CheckoutPage from './pages/CheckoutPage.jsx';
import VerifyEmailPage from './pages/VerifyEmailPage.jsx';
import ResetPasswordPage from './pages/ResetPasswordPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

export default function App() {
  return (
    <ToastProvider>
      <AuthProvider>
        <BookingCartProvider>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/perfil"
              element={
                <ProtectedRoute>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute adminOnly>
                  <AdminPage />
                </ProtectedRoute>
              }
            />
            <Route path="/reservas/:id" element={<ProtectedRoute><BookingDetailPage /></ProtectedRoute>} />
            <Route path="/habitaciones/:id" element={<SuiteDetailPage />} />
            <Route path="/pagar/:id" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
            <Route path="/verificar-email/:token" element={<VerifyEmailPage />} />
            <Route path="/restablecer-contrasena" element={<ResetPasswordPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
          <AuthModals />
        </BookingCartProvider>
      </AuthProvider>
    </ToastProvider>
  );
}
