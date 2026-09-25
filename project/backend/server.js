import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import helmet from 'helmet';
import compression from 'compression';
import connectDB, { getConnectionStatus, startConnectionMonitoring, closeConnection } from './database/db.js';
import authRoutes from './routes/auth.js';
import suiteRoutes from './routes/suites.js';
import experienceRoutes from './routes/experiences.js';
import bookingRoutes from './routes/bookings.js';
import availabilityRoutes from './routes/availability.js';
import chatRoutes from './routes/chat.js';
import { adminMiddleware, authMiddleware } from './middleware/auth.js';
import SuiteNight from './models/SuiteNight.js';
import { isEmailConfigured } from './utils/email.js';
import { runStartupBootstrap } from './bootstrap.js';
import { 
  errorHandler, 
  notFoundHandler, 
  jsonErrorHandler,
  asyncHandler 
} from './middleware/errorHandler.js';

// Cargar variables de entorno
dotenv.config();

// ============================================
// CONFIGURACIÓN INICIAL
// ============================================

const app = express();

// Detrás de un proxy (nginx en Docker) Express solo ve la IP del proxy.
// Con TRUST_PROXY=1, req.ip es la IP real del visitante; sin esto, el limitador
// de login y el del chat tratarían a TODOS los usuarios como uno solo.
if (process.env.TRUST_PROXY) {
  app.set('trust proxy', Number(process.env.TRUST_PROXY) || process.env.TRUST_PROXY);
}
const PORT = process.env.PORT || 5000;
const isProduction = process.env.NODE_ENV === 'production';
const isDevelopment = process.env.NODE_ENV === 'development';

// ============================================
// FUNCIONES AUXILIARES
// ============================================

/**
 * Logging de requests en desarrollo
 */
const requestLogger = (req, res, next) => {
  const startTime = Date.now();
  
  res.on('finish', () => {
    const duration = Date.now() - startTime;
    const status = res.statusCode;
    const statusColor = status >= 500 ? '\x1b[31m' : status >= 400 ? '\x1b[33m' : '\x1b[32m';
    
    console.log(
      `${statusColor}[${status}]\x1b[0m ${req.method} ${req.url} - ${duration}ms`
    );
  });
  
  next();
};

/**
 * Headers de seguridad básicos.
 * Antes se ponían "a mano" (y X-XSS-Protection, que los navegadores
 * modernos ya ignoran). Se sustituye por helmet, que además añade
 * Strict-Transport-Security, Referrer-Policy y una Content-Security-Policy
 * razonable para una API JSON.
 */
const securityHeaders = helmet({
  contentSecurityPolicy: false, // esta app es una API JSON, no sirve HTML
  crossOriginResourcePolicy: { policy: 'cross-origin' }
});

/**
 * Graceful shutdown del servidor
 */
const setupGracefulShutdown = (server) => {
  const shutdown = async (signal) => {
    console.log(`\n⚠️ Recibida señal ${signal}. Cerrando conexiones...`);
    
    // Cerrar servidor HTTP
    server.close(async () => {
      console.log('✅ Servidor HTTP cerrado');
      
      // Cerrar conexión a MongoDB
      await closeConnection();
      
      console.log('👋 Servidor detenido correctamente');
      process.exit(0);
    });
    
    // Forzar cierre después de 10 segundos
    setTimeout(() => {
      console.error('❌ Timeout cerrando conexiones. Forzando salida...');
      process.exit(1);
    }, 10000);
  };
  
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
};

/**
 * Verifica variables de entorno requeridas
 */
const validateEnvVariables = () => {
  const required = ['JWT_SECRET'];
  const missing = required.filter(key => !process.env[key]);
  
  if (missing.length > 0) {
    throw new Error(`Variables de entorno faltantes: ${missing.join(', ')}`);
  }
  
  if (isProduction && !process.env.MONGODB_URI.includes('mongodb+srv')) {
    console.warn('⚠️ Advertencia: En producción se recomienda usar MongoDB Atlas');
  }
  
  // Secretos débiles o de ejemplo (OPS-012). En un despliegue real (FRONTEND_URL
  // no es localhost) impiden el arranque; en local solo avisan, para que el
  // "docker compose up" de prueba siga funcionando.
  const looksLocal = /localhost|127\.0\.0\.1/.test(process.env.FRONTEND_URL || 'http://localhost');
  const problems = [];
  const jwt = process.env.JWT_SECRET;
  if (jwt.length < 32 || /cambia|changeme|example|ejemplo|secret(o)?$/i.test(jwt)) {
    problems.push('JWT_SECRET es corto (<32) o parece un valor de ejemplo');
  }
  try {
    const dbPassword = decodeURIComponent(new URL(process.env.MONGODB_URI || '').password || '');
    if (dbPassword && (dbPassword.length < 12 || /^(1234|password|admin|mongo|123456)/i.test(dbPassword))) {
      problems.push('La contraseña de MongoDB es débil (<12 caracteres o común)');
    }
  } catch { /* URI sin credenciales o no parseable: nada que comprobar */ }
  
  if (isProduction && problems.length) {
    const msg = `Configuración insegura: ${problems.join('; ')}`;
    if (!looksLocal) throw new Error(`${msg}. Genera valores nuevos (por ejemplo: openssl rand -hex 32).`);
    console.warn(`⚠️ ${msg} (se permite solo porque FRONTEND_URL apunta a localhost).`);
  }
  
  if (isProduction && !isEmailConfigured()) {
    console.error('❌ SMTP no configurado: la recuperación de contraseña y la verificación de email NO entregarán correos. Configura SMTP_HOST, SMTP_USER y SMTP_PASS.');
  }
  
  console.log('✅ Variables de entorno validadas');
};

// ============================================
// CONFIGURACIÓN DE MIDDLEWARES
// ============================================

/**
 * Configura todos los middlewares de la aplicación
 */
const setupMiddlewares = () => {
  // CORS - Configuración para producción
  const corsOptions = {
    origin: isProduction 
      ? [process.env.FRONTEND_URL || 'https://tudominio.com', 'http://localhost:3000']
      : '*',
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
  };
  
  app.use(cors(corsOptions));
  
  // Parseo de JSON con límite
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));
  
  // Manejo de errores JSON
  app.use(jsonErrorHandler);
  
  // Headers de seguridad
  app.use(securityHeaders);
  
  // Logging
  if (isDevelopment) {
    app.use(requestLogger);
  }
  
  // Compresión de respuestas.
  // Antes: import('express-compression') — ese paquete no está entre las
  // dependencias (la que sí está instalada es "compression"), así que
  // esto fallaba en silencio en cada arranque en producción y nunca
  // comprimía nada. Además, al ser una promesa sin esperar, dejaba una
  // ventana donde el servidor podía atender peticiones antes de que el
  // middleware quedara registrado. Ahora es una importación normal.
  if (isProduction) {
    app.use(compression());
  }
};

// ============================================
// CONFIGURACIÓN DE RUTAS
// ============================================

/**
 * Configura todas las rutas de la API
 */
const setupRoutes = () => {
  // Rutas de la API
  app.use('/api/auth', authRoutes);
  app.use('/api/suites', suiteRoutes);
  app.use('/api/experiences', experienceRoutes);
  app.use('/api/bookings', bookingRoutes);
  app.use('/api/availability', availabilityRoutes);
  app.use('/api/chat', chatRoutes);
  
  // Endpoint de salud (monitoreo). Solo expone lo mínimo necesario para
  // un healthcheck (Docker, un balanceador, uptime monitors). Antes
  // devolvía process.memoryUsage() completo y el estado detallado de la
  // base de datos a CUALQUIERA sin autenticar — información útil para
  // alguien reconociendo el servidor antes de atacarlo. El detalle
  // completo ahora vive en /api/health/detailed, protegido para admins.
  app.get('/api/health', asyncHandler(async (req, res) => {
    const dbStatus = getConnectionStatus();
    res.json({
      success: true,
      status: dbStatus?.isConnected ? 'OK' : 'DEGRADED',
      timestamp: new Date().toISOString()
    });
  }));

  // Igual que arriba, pero con todo el detalle, solo para administradores
  // autenticados.
  app.get('/api/health/detailed', authMiddleware, adminMiddleware, asyncHandler(async (req, res) => {
    const dbStatus = getConnectionStatus();
    res.json({
      success: true,
      status: 'OK',
      timestamp: new Date().toISOString(),
      uptime: Math.floor(process.uptime()),
      environment: process.env.NODE_ENV || 'development',
      database: dbStatus,
      memory: process.memoryUsage(),
      version: process.env.npm_package_version || '1.0.0'
    });
  }));
  
  // Endpoint raíz
  app.get('/', (req, res) => {
    res.json({
      name: 'Palacio del Mar API',
      version: '1.0.0',
      status: 'Operational',
      endpoints: {
        auth: '/api/auth',
        suites: '/api/suites',
        experiences: '/api/experiences',
        bookings: '/api/bookings',
        availability: '/api/availability',
        chat: '/api/chat',
        health: '/api/health'
      },
      documentation: '/api/docs' // Si tienes documentación
    });
  });
  
  // Ruta 404 para rutas no encontradas
  app.use(notFoundHandler);
};

// ============================================
// CONFIGURACIÓN DE DOCUMENTACIÓN (Opcional)
// ============================================

/**
 * Configura documentación de la API (si tienes swagger)
 */
const setupDocs = async () => {
  if (process.env.ENABLE_DOCS === 'true') {
    try {
      const swaggerUi = await import('swagger-ui-express');
      const swaggerDocument = await import('./swagger.json');
      app.use('/api/docs', swaggerUi.serve, swaggerUi.setup(swaggerDocument));
      console.log('📚 Documentación API disponible en /api/docs');
    } catch (error) {
      console.log('⚠️ Documentación API no disponible');
    }
  }
};

// ============================================
// INICIO DEL SERVIDOR
// ============================================

/**
 * Inicia el servidor con todas las configuraciones
 */
const startServer = async () => {
  try {
    // Guardar tiempo de inicio
    process.startTime = Date.now();
    
    // 1. Validar variables de entorno
    validateEnvVariables();
    
    // 2. Conectar a MongoDB
    console.log('\n📡 Conectando a MongoDB...');
    await connectDB();
    console.log('✅ MongoDB conectado correctamente');
    
    // La garantía contra la doble reserva es el índice ÚNICO (suite, date):
    // hay que esperar a que exista antes de aceptar reservas.
    await SuiteNight.init();
    
    // 2b. Cargar catálogo de ejemplo y crear el admin si hace falta
    // (DEPLOY-002): así "docker compose up" a secas deja el sitio ya
    // navegable y con una cuenta para entrar a /admin, sin comandos aparte.
    await runStartupBootstrap();
    
    // 3. Configurar middlewares
    console.log('🔧 Configurando middlewares...');
    setupMiddlewares();
    
    // 4. Configurar rutas
    console.log('🛣️ Configurando rutas...');
    setupRoutes();
    
    // 5. Configurar documentación (opcional)
    await setupDocs();
    
    // 6. Manejador de errores (siempre al final)
    app.use(errorHandler);
    
    // 7. Iniciar monitoreo en producción
    if (isProduction) {
      console.log('📊 Iniciando monitoreo de producción...');
      startConnectionMonitoring();
    }
    
    // 8. Iniciar servidor HTTP
    const server = app.listen(PORT, () => {
      console.log('\n' + '='.repeat(50));
      console.log(`✨ PALACIO DEL MAR API`);
      console.log('='.repeat(50));
      console.log(`🚀 Servidor: http://localhost:${PORT}`);
      console.log(`🌍 Entorno: ${process.env.NODE_ENV || 'development'}`);
      console.log(`📡 Health: http://localhost:${PORT}/api/health`);
      console.log(`🕐 Iniciado: ${new Date().toLocaleString()}`);
      console.log('='.repeat(50) + '\n');
    });
    
    // 9. Configurar graceful shutdown
    setupGracefulShutdown(server);
    
  } catch (error) {
    console.error('\n❌ Error fatal al iniciar el servidor:');
    console.error(`📋 Mensaje: ${error.message}`);
    
    if (error.stack && isDevelopment) {
      console.error(`\n📚 Stack trace:\n${error.stack}`);
    }
    
    console.error('\n💡 Posibles soluciones:');
    console.error('1. Verifica que MongoDB esté corriendo');
    console.error('2. Revisa las variables de entorno en .env');
    console.error('3. Asegúrate de que el puerto no esté en uso');
    console.error('4. Verifica que todas las dependencias estén instaladas');
    
    process.exit(1);
  }
};

// ============================================
// MANEJO DE ERRORES NO CAPTURADOS
// ============================================

/**
 * Maneja errores no capturados por try/catch
 */
process.on('uncaughtException', (error) => {
  console.error('\n💥 Error no capturado:');
  console.error(error);
  
  if (isProduction) {
    console.log('⚠️ Reiniciando servidor...');
    process.exit(1);
  }
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('\n💥 Promesa rechazada no manejada:');
  console.error('Razón:', reason);
  console.error('Promesa:', promise);
  
  if (isProduction) {
    console.log('⚠️ Reiniciando servidor...');
    process.exit(1);
  }
});

// ============================================
// EJECUTAR SERVIDOR
// ============================================

startServer();

export default app;