// En la parte superior, con los demás imports:
import catalogRoutes from './routes/catalog.routes.js';
import healthRoutes from './routes/health.routes.js';
import availabilityRoutes from './routes/availability.routes.js';

app.use('/api', catalogRoutes);

// Más abajo, donde configuras los endpoints (ej. app.use('/api/...')):

app.use('/health', healthRoutes);
app.use('/api/availability', availabilityRoutes);
