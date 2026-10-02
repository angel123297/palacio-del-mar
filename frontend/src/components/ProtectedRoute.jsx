import { useEffect } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function ProtectedRoute({ children, adminOnly = false }) {
  const { user, loading, isAuthenticated, isAdmin, setAuthModal } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (!loading && !isAuthenticated) {
      setAuthModal('login');
    }
  }, [loading, isAuthenticated, setAuthModal]);

  if (loading) {
    return (
      <div className="page-loading">
        <div className="spinner" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  if (adminOnly && !isAdmin) {
    return (
      <div className="page-loading">
        <div className="empty-state">
          <p>No tienes permisos para ver esta página.</p>
          <button className="btn-primary" onClick={() => navigate('/')}>Volver al inicio</button>
        </div>
      </div>
    );
  }

  return children;
}
