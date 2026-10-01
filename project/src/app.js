// En la parte superior, con los demás imports:
import catalogRoutes from './routes/catalog.routes.js';
import healthRoutes from './routes/health.routes.js';

app.use('/api', catalogRoutes);

// Más abajo, donde configuras los endpoints (ej. app.use('/api/...')):

app.use('/health', healthRoutes);
