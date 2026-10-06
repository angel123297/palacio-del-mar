import mongoose from 'mongoose';
import dotenv from 'dotenv';

dotenv.config();

// ============================================
// CONFIGURACIÓN DE CONEXIÓN
// ============================================

const CONNECTION_OPTIONS = {
  serverSelectionTimeoutMS: 5000,
  socketTimeoutMS: 45000,
  connectTimeoutMS: 10000,
  heartbeatFrequencyMS: 10000,
  
  retryWrites: true,
  retryReads: true,
  
  maxPoolSize: 10,
  minPoolSize: 2,
  
  family: 4,
  ssl: process.env.MONGODB_TLS === 'true'
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
  // Cadena local por defecto apuntando al contenedor 'mongo' de Docker
  const uri = process.env.MONGODB_URI || 'mongodb://mongo:27017/palacio_del_mar';
  
  const isValidFormat = uri.startsWith('mongodb://') || uri.startsWith('mongodb+srv://');
  if (!isValidFormat) {
    return 'mongodb://mongo:27017/palacio_del_mar';
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
  } else if (/authentication/i.test(error.message) || error.code === 18) {
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

let listenersRegistered = false;

const setupEventListeners = () => {
  if (listenersRegistered) return;
  listenersRegistered = true;

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

// ============================================
// FUNCIÓN PRINCIPAL DE CONEXIÓN
// ============================================

const connectDB = async () => {
  try {
    const uri = validateMongoURI();
    
    if (process.env.NODE_ENV === 'development') {
      mongoose.set('debug', false);
    }

    const options = {
      ...CONNECTION_OPTIONS,
      dbName: process.env.DB_NAME || getDbNameFromUri(uri)
    };
    
    setupEventListeners();
    
    console.log('🔄 Conectando a MongoDB Local...');
    const conn = await mongoose.connect(uri, options);
    
    isConnected = true;
    connectionAttempts = 0;
    
    console.log(`\n✅ MongoDB Conectado Correctamente`);
    console.log(`📊 Base de datos: ${conn.connection.name}`);
    console.log(`🌐 Host: ${conn.connection.host}:${conn.connection.port}`);
    console.log(`📦 Modelos cargados: ${Object.keys(conn.models).length}`);
    
    return conn;
    
  } catch (error) {
    console.error(`\n❌ Error inicial conectando a MongoDB:`);
    console.error(`📋 Mensaje: ${error.message}`);
    
    const shouldRetry = await handleConnectionError(error, process.env.MONGODB_URI);
    
    if (shouldRetry) {
      return connectDB();
    }
    
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
    }, 300000);
  }
};

export default connectDB;