import path from 'path';
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

if (process.env.TRUST_PROXY) {
  app.set('trust proxy', Number(process.env.TRUST_PROXY) || process.env.TRUST_PROXY);
}

// Puerto fijado en 8080 por defecto
const PORT = process.env.PORT || 8080;
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
 * Headers de seguridad básicos con Helmet
 */
const securityHeaders = helmet({
  contentSecurityPolicy: false,
  crossOriginResourcePolicy: { policy: 'cross-origin' }
});

/**
 * Graceful shutdown del servidor
 */
const setupGracefulShutdown = (server) => {
  const shutdown = async (signal) => {
    console.log(`\n⚠️ Recibida señal ${signal}. Cerrando conexiones...`);
    
    server.close(async () => {
      console.log('✅ Servidor HTTP cerrado');
      await closeConnection();
      console.log('👋 Servidor detenido correctamente');
      process.exit(0);
    });
    
    setTimeout(() => {
      console.error('❌ Timeout cerrando conexiones. Forzando salida...');
      process.exit(1);
    }, 10000);
  };
  
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
};

/**
 * Garantiza valores por defecto locales sin requerir archivo .env ni Atlas
 */
const validateEnvVariables = () => {
  process.env.PORT = process.env.PORT || '8080';
  process.env.FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:8080';
  process.env.JWT_SECRET = process.env.JWT_SECRET || 'palacio_del_mar_jwt_secret_key_2026_local_32chars';
  process.env.MONGODB_URI = process.env.MONGODB_URI || 'mongodb://mongo:27017/palacio_del_mar';

  console.log('✅ Entorno inicializado localmente en puerto 8080 (Sin dependencia de .env)');
};

// ============================================
// CONFIGURACIÓN DE MIDDLEWARES
// ============================================

const setupMiddlewares = () => {
  const corsOptions = {
    origin: isProduction 
      ? [process.env.FRONTEND_URL || 'http://localhost:8080', 'http://localhost:3000']
      : '*',
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
  };
  
  app.use(cors(corsOptions));
  app.use(express.json({ limit: '1mb' }));
  app.use(express.urlencoded({ extended: true, limit: '1mb' }));

  // Middleware para servir las imágenes subidas dinámicamente
  app.use('/uploads', express.static(path.join(process.cwd(), 'uploads')));

  app.use(jsonErrorHandler);
  app.use(securityHeaders);
  
  if (isDevelopment) {
    app.use(requestLogger);
  }
  
  if (isProduction) {
    app.use(compression());
  }
};

// ============================================
// CONFIGURACIÓN DE RUTAS
// ============================================

const setupRoutes = () => {
  app.use('/api/auth', authRoutes);
  app.use('/api/suites', suiteRoutes);
  app.use('/api/experiences', experienceRoutes);
  app.use('/api/bookings', bookingRoutes);
  app.use('/api/availability', availabilityRoutes);
  app.use('/api/chat', chatRoutes);
  
  app.get('/api/health', asyncHandler(async (req, res) => {
    const dbStatus = getConnectionStatus();
    res.json({
      success: true,
      status: dbStatus?.isConnected ? 'OK' : 'DEGRADED',
      timestamp: new Date().toISOString()
    });
  }));

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
      documentation: '/api/docs'
    });
  });
  
  app.use(notFoundHandler);
};

// ============================================
// DOCUMENTACIÓN (OPCIONAL)
// ============================================

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

const startServer = async () => {
  try {
    process.startTime = Date.now();
    
    validateEnvVariables();
    
    console.log('\n📡 Conectando a MongoDB Local...');
    await connectDB();
    console.log('✅ MongoDB conectado correctamente');
    
    await SuiteNight.init();
    await runStartupBootstrap();
    
    console.log('🔧 Configurando middlewares...');
    setupMiddlewares();
    
    console.log('🛣️ Configurando rutas...');
    setupRoutes();
    
    await setupDocs();
    
    app.use(errorHandler);
    
    if (isProduction) {
      console.log('📊 Iniciando monitoreo de producción...');
      startConnectionMonitoring();
    }
    
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
    
    setupGracefulShutdown(server);
    
  } catch (error) {
    console.error('\n❌ Error fatal al iniciar el servidor:');
    console.error(`📋 Mensaje: ${error.message}`);
    
    if (error.stack && isDevelopment) {
      console.error(`\n📚 Stack trace:\n${error.stack}`);
    }
    
    process.exit(1);
  }
};

// ============================================
// MANEJO DE ERRORES NO CAPTURADOS
// ============================================

process.on('uncaughtException', (error) => {
  console.error('\n💥 Error no capturado:');
  console.error(error);
  if (isProduction) process.exit(1);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('\n💥 Promesa rechazada no manejada:');
  console.error('Razón:', reason);
  if (isProduction) process.exit(1);
});

// ============================================
// EJECUTAR SERVIDOR
// ============================================

startServer();

export default app;