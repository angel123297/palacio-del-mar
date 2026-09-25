import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En Docker, nginx reenvía /api al backend (ver frontend/nginx.conf).
// En desarrollo local ("npm run dev"), este proxy hace lo mismo: así el
// frontend siempre llama a la ruta relativa "/api", sin necesitar variables
// de entorno ni reconstruir la imagen para apuntar a otro backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:5000',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
});
