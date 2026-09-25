import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [authModal, setAuthModal] = useState(null); // 'login' | 'register' | 'forgot' | null

  const loadMe = useCallback(async () => {
    const token = localStorage.getItem('palacio_token');
    if (!token) {
      setLoading(false);
      return;
    }
    try {
      const res = await api.get('/auth/me');
      // El endpoint /auth/me responde { success, user } en la raíz, NO
      // { success, data: { user } }. La versión anterior del frontend
      // guardaba la respuesta completa (incluido "success") como si fuera
      // el usuario, así que el nombre, email, rol, etc. desaparecían del
      // perfil en cuanto se recargaba la página.
      setUser(res.data.user);
    } catch {
      localStorage.removeItem('palacio_token');
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadMe();
    const onUnauthorized = () => setUser(null);
    window.addEventListener('palacio:unauthorized', onUnauthorized);
    return () => window.removeEventListener('palacio:unauthorized', onUnauthorized);
  }, [loadMe]);

  const login = useCallback(async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    localStorage.setItem('palacio_token', res.data.token);
    setUser(res.data.user);
    setAuthModal(null);
    return res.data.user;
  }, []);

  const register = useCallback(async (name, email, password) => {
    const res = await api.post('/auth/register', { name, email, password });
    localStorage.setItem('palacio_token', res.data.token);
    setUser(res.data.user);
    setAuthModal(null);
    return res.data.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // Si el token ya venció, el backend igual respondería 401; de todas
      // formas limpiamos la sesión local.
    }
    localStorage.removeItem('palacio_token');
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    const res = await api.get('/auth/me');
    setUser(res.data.user);
    return res.data.user;
  }, []);

  const value = {
    user,
    loading,
    isAuthenticated: !!user,
    isAdmin: user?.role === 'admin',
    login,
    register,
    logout,
    refreshUser,
    authModal,
    setAuthModal
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
};
