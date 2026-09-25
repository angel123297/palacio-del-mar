import mongoose from 'mongoose';
import dotenv from 'dotenv';

dotenv.config();

// ============================================
// CONFIGURACIÓN DE CONEXIÓN
// ============================================

const CONNECTION_OPTIONS = {
  // Timeouts
  serverSelectionTimeoutMS: 5000,
  socketTimeoutMS: 45000,
  connectTimeoutMS: 10000,
  heartbeatFrequencyMS: 10000,
  
  // Retry logic
  retryWrites: true,
  retryReads: true,
  
  // Pool de conexiones
  maxPoolSize: 10,
  minPoolSize: 2,
  
  // Forzar IPv4 (evita problemas con IPv6)
  family: 4,
  
  // SSL para producción
  // TLS: activo por defecto en producción (MongoDB Atlas).
  // Con MongoDB dentro de Docker (sin TLS) se desactiva con MONGODB_TLS=false.
  ssl: process.env.MONGODB_TLS
    ? process.env.MONGODB_TLS === 'true'
    : process.env.NODE_ENV === 'production',

  // ✅ ELIMINADO: debug no es una opción válida de mongoose.connect()
  // Se configura por separado con mongoose.set('debug', true)
};

// ============================================
// VARIABLES DE ESTADO
// ============================================

let isConnected = false;
let connectionAttempts = 0;
const MAX_RETRY_ATTEMPTS = 5;
const RETRY_DELAY_MS = 5000;

// ============================================
// FUNCIONES AUXILIARES
// ============================================

const validateMongoURI = () => {
  const uri = process.env.MONGODB_URI;
  
  if (!uri) {
    throw new Error(
      '❌ MONGODB_URI no está definida en las variables de entorno.\n' +
      'Por favor, configura MONGODB_URI en tu archivo .env\n' +
      'Ejemplo: MONGODB_URI=mongodb://localhost:27017/palacio_del_mar'
    );
  }
  
  const isValidFormat = uri.startsWith('mongodb://') || uri.startsWith('mongodb+srv://');
  if (!isValidFormat) {
    throw new Error(
      '❌ Formato de MONGODB_URI inválido.\n' +
      'Debe comenzar con mongodb:// o mongodb+srv://\n' +
      'Ejemplo: mongodb://localhost:27017/palacio_del_mar'
    );
  }
  
  return uri;
};

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

const handleConnectionError = async (error, uri) => {
  console.error(`❌ Error de conexión MongoDB: ${error.message}`);
  
  if (error.name === 'MongoNetworkError') {
    console.log('🌐 Error de red - Verifica que MongoDB esté corriendo');
  } else if (error.name === 'MongoServerSelectionError') {
    console.log('🔍 No se pudo seleccionar un servidor - Verifica la URI y la red');
  } else if (error.code === 'ECONNREFUSED') {
    console.log('🚫 Conexión rechazada - Asegúrate de que MongoDB esté iniciado');
  } else if (error.message.includes('authentication')) {
    console.log('🔐 Error de autenticación - Verifica usuario y contraseña');
    return false;
  }
  
  connectionAttempts++;
  
  if (connectionAttempts <= MAX_RETRY_ATTEMPTS) {
    console.log(`🔄 Reintentando conexión en ${RETRY_DELAY_MS/1000}s... (Intento ${connectionAttempts}/${MAX_RETRY_ATTEMPTS})`);
    await delay(RETRY_DELAY_MS);
    return true;
  }
  
  console.error(`❌ No se pudo conectar después de ${MAX_RETRY_ATTEMPTS} intentos`);
  return false;
};

// ============================================
// EVENTOS DE CONEXIÓN
// ============================================

const setupEventListeners = () => {
  mongoose.connection.on('connected', () => {
    isConnected = true;
    connectionAttempts = 0;
    console.log(`✅ MongoDB Conectado - Base de datos: ${mongoose.connection.name}`);
    console.log(`📊 Host: ${mongoose.connection.host}:${mongoose.connection.port}`);
    console.log(`🔗 Estado: ${mongoose.connection.readyState === 1 ? 'Conectado' : 'Desconectado'}`);
  });

  mongoose.connection.on('error', (err) => {
    isConnected = false;
    console.error(`❌ Error en MongoDB: ${err.message}`);
  });

  mongoose.connection.on('disconnected', () => {
    isConnected = false;
    console.log('⚠️ MongoDB desconectado. Intentando reconectar...');
  });

  mongoose.connection.on('reconnected', () => {
    isConnected = true;
    console.log('🔄 MongoDB reconectado exitosamente');
  });

  mongoose.connection.on('reconnectFailed', () => {
    console.error('❌ Falló la reconexión a MongoDB después de múltiples intentos');
  });
};

// NOTA: el apagado ordenado (SIGINT/SIGTERM) se gestiona una sola vez en
// server.js, que cierra primero el servidor HTTP y luego llama a
// closeConnection(). Tener otro listener aquí con process.exit(0) directo
// provocaba un apagado duplicado y no esperaba a que el servidor HTTP
// terminara de responder las peticiones en curso.

// ============================================
// FUNCIÓN PRINCIPAL DE CONEXIÓN
// ============================================

const connectDB = async () => {
  try {
    const uri = validateMongoURI();
    
    // ✅ Configurar debug por separado, no dentro de las opciones de connect()
    if (process.env.NODE_ENV === 'development') {
      mongoose.set('debug', false); // Cambiar a true si quieres ver las queries en consola
    }

    const options = {
      ...CONNECTION_OPTIONS,
      dbName: process.env.DB_NAME || getDbNameFromUri(uri)
    };
    
    setupEventListeners();
    
    console.log('🔄 Conectando a MongoDB...');
    const conn = await mongoose.connect(uri, options);
    
    isConnected = true;
    connectionAttempts = 0;
    
    console.log(`\n✅ MongoDB Conectado Correctamente`);
    console.log(`📊 Base de datos: ${conn.connection.name}`);
    console.log(`🌐 Host: ${conn.connection.host}:${conn.connection.port}`);
    console.log(`📦 Modelos cargados: ${Object.keys(conn.models).length}`);
    
    if (process.env.NODE_ENV === 'development') {
      console.log(`\n🔧 Modo Desarrollo`);
      console.log(`📡 Pool de conexiones: ${CONNECTION_OPTIONS.maxPoolSize}`);
    }
    
    return conn;
    
  } catch (error) {
    console.error(`\n❌ Error inicial conectando a MongoDB:`);
    console.error(`📋 Mensaje: ${error.message}`);
    
    const shouldRetry = await handleConnectionError(error, process.env.MONGODB_URI);
    
    if (shouldRetry) {
      return connectDB();
    }
    
    console.error('\n💡 Soluciones posibles:');
    console.error('1. Verifica que MongoDB esté instalado y corriendo:');
    console.error('   - Windows: net start MongoDB');
    console.error('   - Mac: brew services start mongodb-community');
    console.error('   - Linux: sudo systemctl start mongod');
    console.error('2. Verifica tu cadena de conexión en el archivo .env');
    console.error('3. Si usas MongoDB Atlas, verifica tu usuario/contraseña');
    console.error('4. Asegúrate de que no haya firewalls bloqueando el puerto 27017');
    
    process.exit(1);
  }
};

// ============================================
// FUNCIONES UTILITARIAS
// ============================================

const getDbNameFromUri = (uri) => {
  try {
    const match = uri.match(/\/([^/?]+)(\?|$)/);
    if (match && match[1]) return match[1];
    
    const atlasMatch = uri.match(/\.net\/([^?]+)/);
    if (atlasMatch && atlasMatch[1]) return atlasMatch[1];
    
    return 'palacio_del_mar';
  } catch {
    return 'palacio_del_mar';
  }
};

export const getConnectionStatus = () => {
  const states = {
    0: 'Desconectado',
    1: 'Conectado',
    2: 'Conectando',
    3: 'Desconectando'
  };
  
  return {
    isConnected: mongoose.connection.readyState === 1,
    readyState: states[mongoose.connection.readyState] || 'Desconocido',
    host: mongoose.connection.host,
    port: mongoose.connection.port,
    name: mongoose.connection.name,
    models: Object.keys(mongoose.connection.models).length,
    poolSize: CONNECTION_OPTIONS.maxPoolSize
  };
};

export const closeConnection = async () => {
  if (mongoose.connection.readyState === 1) {
    console.log('🔌 Cerrando conexión a MongoDB...');
    await mongoose.connection.close();
    isConnected = false;
    console.log('✅ Conexión cerrada');
  }
};

export const startConnectionMonitoring = () => {
  if (process.env.NODE_ENV === 'production') {
    setInterval(() => {
      const status = getConnectionStatus();
      
      if (!status.isConnected) {
        console.warn('⚠️ Advertencia: Conexión a MongoDB perdida');
      }
      
      if (process.env.MONITORING_LOG === 'true') {
        console.log(`📊 MongoDB Status: ${status.readyState} | Pool: ${status.poolSize}`);
      }
    }, 300000);
  }
};

export default connectDB;
