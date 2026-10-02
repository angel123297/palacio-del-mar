import axios from 'axios';

// Siempre relativa: en Docker, nginx reenvía /api al backend; en
// desarrollo local, el proxy de Vite hace lo mismo (ver vite.config.js).
// Así nunca hace falta reconstruir la imagen para apuntar a otra URL.
const api = axios.create({
  baseURL: '/api',
  timeout: 15000
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('palacio_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Si el token expiró o es inválido, limpiamos la sesión local. No forzamos
// una recarga de página: dejamos que cada pantalla reaccione (por ejemplo
// mostrando el modal de login) para no perder lo que el usuario esté
// escribiendo en ese momento.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('palacio_token');
      window.dispatchEvent(new CustomEvent('palacio:unauthorized'));
    }
    return Promise.reject(error);
  }
);

export default api;
