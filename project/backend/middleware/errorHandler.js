import mongoose from 'mongoose';
import jwt from 'jsonwebtoken';

// ============================================
// CONFIGURACIÓN
// ============================================

// Tipos de errores conocidos
const ERROR_TYPES = {
  // Errores de validación
  VALIDATION: 'VALIDATION_ERROR',
  CAST: 'CAST_ERROR',
  DUPLICATE: 'DUPLICATE_KEY_ERROR',
  
  // Errores de autenticación
  JWT: 'JWT_ERROR',
  UNAUTHORIZED: 'UNAUTHORIZED_ERROR',
  FORBIDDEN: 'FORBIDDEN_ERROR',
  
  // Errores de base de datos
  DATABASE: 'DATABASE_ERROR',
  MONGO_NETWORK: 'MONGO_NETWORK_ERROR',
  
  // Errores de negocio
  NOT_FOUND: 'NOT_FOUND_ERROR',
  CONFLICT: 'CONFLICT_ERROR',
  RATE_LIMIT: 'RATE_LIMIT_ERROR',
  
  // Errores de servidor
  SERVER: 'SERVER_ERROR'
};

// Códigos HTTP con mensajes predeterminados
const HTTP_STATUS = {
  400: { name: 'Bad Request', defaultMessage: 'Solicitud inválida' },
  401: { name: 'Unauthorized', defaultMessage: 'No autorizado' },
  403: { name: 'Forbidden', defaultMessage: 'Acceso denegado' },
  404: { name: 'Not Found', defaultMessage: 'Recurso no encontrado' },
  409: { name: 'Conflict', defaultMessage: 'Conflicto con el estado actual' },
  422: { name: 'Unprocessable Entity', defaultMessage: 'Error de validación' },
  429: { name: 'Too Many Requests', defaultMessage: 'Demasiadas solicitudes' },
  500: { name: 'Internal Server Error', defaultMessage: 'Error interno del servidor' },
  503: { name: 'Service Unavailable', defaultMessage: 'Servicio no disponible' }
};

// ============================================
// LOGGING ESTRUCTURADO
// ============================================

/**
 * Log estructurado de errores
 */
const logError = (error, req, context = {}) => {
  const logEntry = {
    timestamp: new Date().toISOString(),
    type: error.type || ERROR_TYPES.SERVER,
    message: error.message,
    status: error.status || 500,
    path: req?.path,
    method: req?.method,
    ip: req?.ip || req?.headers?.['x-forwarded-for'] || 'unknown',
    userAgent: req?.headers?.['user-agent'] || 'unknown',
    userId: req?.user?.id,
    ...context
  };

  // Añadir stack trace en desarrollo
  if (process.env.NODE_ENV === 'development') {
    logEntry.stack = error.stack;
  }

  // Usar nivel de log según severidad
  if (logEntry.status >= 500) {
    console.error(`[ERROR] ${JSON.stringify(logEntry)}`);
  } else if (logEntry.status >= 400) {
    console.warn(`[WARN] ${JSON.stringify(logEntry)}`);
  } else {
    console.log(`[INFO] ${JSON.stringify(logEntry)}`);
  }

  // En producción, enviar a servicio de monitoreo
  if (process.env.NODE_ENV === 'production' && process.env.SENTRY_DSN) {
    // Integración con Sentry (opcional)
    // Sentry.captureException(error, { extra: logEntry });
  }

  return logEntry;
};

// ============================================
// CLASIFICACIÓN DE ERRORES
// ============================================

/**
 * Clasifica y formatea errores de MongoDB
 */
const handleMongoError = (err) => {
  // Error de validación de Mongoose
  if (err instanceof mongoose.Error.ValidationError) {
    const errors = Object.values(err.errors).map(e => ({
      field: e.path,
      message: e.message,
      value: e.value
    }));

    return {
      type: ERROR_TYPES.VALIDATION,
      status: 422,
      message: 'Error de validación',
      details: errors,
      publicMessage: 'Por favor, revisa los datos ingresados'
    };
  }

  // Error de cast (ID inválido)
  if (err instanceof mongoose.Error.CastError) {
    return {
      type: ERROR_TYPES.CAST,
      status: 400,
      message: `ID inválido para el campo "${err.path}"`,
      publicMessage: 'El identificador proporcionado no es válido'
    };
  }

  // Error de duplicado (índice único)
  if (err.code === 11000) {
    const field = Object.keys(err.keyPattern)[0];
    const value = err.keyValue[field];
    
    return {
      type: ERROR_TYPES.DUPLICATE,
      status: 409,
      message: `El valor "${value}" ya existe para el campo "${field}"`,
      publicMessage: `El ${field === 'email' ? 'correo electrónico' : field} ya está registrado`,
      field
    };
  }

  // Error de conexión a MongoDB
  if (err.name === 'MongoNetworkError' || err.name === 'MongoServerSelectionError') {
    return {
      type: ERROR_TYPES.MONGO_NETWORK,
      status: 503,
      message: err.message,
      publicMessage: 'Error de conexión con la base de datos. Por favor, intenta más tarde'
    };
  }

  return null;
};

/**
 * Clasifica y formatea errores de JWT
 */
const handleJWTError = (err) => {
  if (err instanceof jwt.JsonWebTokenError) {
    if (err.name === 'JsonWebTokenError') {
      return {
        type: ERROR_TYPES.JWT,
        status: 401,
        message: 'Token inválido',
        publicMessage: 'Sesión inválida. Por favor, inicia sesión nuevamente'
      };
    }
    if (err.name === 'TokenExpiredError') {
      return {
        type: ERROR_TYPES.JWT,
        status: 401,
        message: 'Token expirado',
        publicMessage: 'Tu sesión ha expirado. Por favor, inicia sesión nuevamente',
        expiredAt: err.expiredAt
      };
    }
  }
  
  return null;
};

/**
 * Clasifica errores personalizados de la aplicación
 */
const handleAppError = (err) => {
  // Error de no encontrado
  if (err.name === 'NotFoundError' || err.status === 404) {
    return {
      type: ERROR_TYPES.NOT_FOUND,
      status: 404,
      message: err.message,
      publicMessage: err.publicMessage || 'El recurso solicitado no existe'
    };
  }

  // Error de no autorizado
  if (err.name === 'UnauthorizedError' || err.status === 401) {
    return {
      type: ERROR_TYPES.UNAUTHORIZED,
      status: 401,
      message: err.message,
      publicMessage: err.publicMessage || 'Debes iniciar sesión para acceder a este recurso'
    };
  }

  // Error de prohibido
  if (err.name === 'ForbiddenError' || err.status === 403) {
    return {
      type: ERROR_TYPES.FORBIDDEN,
      status: 403,
      message: err.message,
      publicMessage: err.publicMessage || 'No tienes permiso para acceder a este recurso'
    };
  }

  // Error de conflicto
  if (err.name === 'ConflictError' || err.status === 409) {
    return {
      type: ERROR_TYPES.CONFLICT,
      status: 409,
      message: err.message,
      publicMessage: err.publicMessage || 'Conflicto con el estado actual del recurso'
    };
  }

  // Error de rate limit
  if (err.name === 'RateLimitError' || err.status === 429) {
    return {
      type: ERROR_TYPES.RATE_LIMIT,
      status: 429,
      message: err.message,
      publicMessage: err.publicMessage || 'Demasiadas solicitudes. Por favor, espera un momento',
      retryAfter: err.retryAfter
    };
  }

  // Error de validación personalizado
  if (err.name === 'ValidationError' || err.status === 422) {
    return {
      type: ERROR_TYPES.VALIDATION,
      status: 422,
      message: err.message,
      details: err.details,
      publicMessage: err.publicMessage || 'Por favor, revisa los datos ingresados'
    };
  }

  return null;
};

// ============================================
// FUNCIONES AUXILIARES
// ============================================

/**
 * Envía respuesta de error al cliente
 */
const sendErrorResponse = (res, error, formattedError) => {
  const { status, type, message, publicMessage, details, stack, retryAfter, ...extra } = formattedError;
  
  const response = {
    success: false,
    error: {
      type,
      message: publicMessage || message,
      status,
      ...(details && { details }),
      ...(retryAfter && { retryAfter }),
      ...(extra && { extra })
    }
  };

  // Añadir información adicional en desarrollo
  if (process.env.NODE_ENV === 'development') {
    response.debug = {
      originalMessage: message,
      stack: stack || error.stack,
      ...extra
    };
  }

  // Añadir headers específicos
  if (retryAfter) {
    res.setHeader('Retry-After', retryAfter);
  }

  res.status(status).json(response);
};

// ============================================
// MIDDLEWARE PRINCIPAL
// ============================================

/**
 * @desc    Manejador global de errores
 * @param   {Error} err - Error object
 * @param   {Object} req - Express request
 * @param   {Object} res - Express response
 * @param   {Function} next - Next middleware
 */
export const errorHandler = (err, req, res, next) => {
  // Log del error
  const logEntry = logError(err, req);
  
  let formattedError = {
    type: ERROR_TYPES.SERVER,
    status: err.status || 500,
    message: err.message,
    stack: err.stack
  };

  // 1. Manejar errores de MongoDB
  const mongoError = handleMongoError(err);
  if (mongoError) {
    formattedError = { ...formattedError, ...mongoError };
  }

  // 2. Manejar errores de JWT
  const jwtError = handleJWTError(err);
  if (jwtError) {
    formattedError = { ...formattedError, ...jwtError };
  }

  // 3. Manejar errores personalizados de la app
  const appError = handleAppError(err);
  if (appError) {
    formattedError = { ...formattedError, ...appError };
  }

  // 4. Si es un error de servidor y estamos en producción, ocultar detalles
  if (formattedError.status >= 500 && process.env.NODE_ENV === 'production') {
    formattedError.message = 'Error interno del servidor';
    formattedError.publicMessage = 'Ha ocurrido un error inesperado. Por favor, intenta más tarde';
    delete formattedError.details;
  }

  // 5. Enviar respuesta
  sendErrorResponse(res, err, formattedError);
};

// ============================================
// CLASES DE ERROR PERSONALIZADAS
// ============================================

/**
 * Error personalizado para la aplicación
 */
export class AppError extends Error {
  constructor(message, status = 500, type = null, publicMessage = null) {
    super(message);
    this.name = this.constructor.name;
    this.status = status;
    this.type = type || this.getTypeFromStatus(status);
    this.publicMessage = publicMessage;
    Error.captureStackTrace(this, this.constructor);
  }

  getTypeFromStatus(status) {
    const typeMap = {
      400: ERROR_TYPES.VALIDATION,
      401: ERROR_TYPES.UNAUTHORIZED,
      403: ERROR_TYPES.FORBIDDEN,
      404: ERROR_TYPES.NOT_FOUND,
      409: ERROR_TYPES.CONFLICT,
      422: ERROR_TYPES.VALIDATION,
      429: ERROR_TYPES.RATE_LIMIT,
      500: ERROR_TYPES.SERVER
    };
    return typeMap[status] || ERROR_TYPES.SERVER;
  }
}

/**
 * Error 404 - No encontrado
 */
export class NotFoundError extends AppError {
  constructor(message = 'Recurso no encontrado', publicMessage = null) {
    super(message, 404, ERROR_TYPES.NOT_FOUND, publicMessage);
  }
}

/**
 * Error 401 - No autorizado
 */
export class UnauthorizedError extends AppError {
  constructor(message = 'No autorizado', publicMessage = null) {
    super(message, 401, ERROR_TYPES.UNAUTHORIZED, publicMessage);
  }
}

/**
 * Error 403 - Prohibido
 */
export class ForbiddenError extends AppError {
  constructor(message = 'Acceso denegado', publicMessage = null) {
    super(message, 403, ERROR_TYPES.FORBIDDEN, publicMessage);
  }
}

/**
 * Error 409 - Conflicto
 */
export class ConflictError extends AppError {
  constructor(message = 'Conflicto con el estado actual', publicMessage = null) {
    super(message, 409, ERROR_TYPES.CONFLICT, publicMessage);
  }
}

/**
 * Error 422 - Validación
 */
export class ValidationError extends AppError {
  constructor(message = 'Error de validación', details = null, publicMessage = null) {
    super(message, 422, ERROR_TYPES.VALIDATION, publicMessage);
    this.details = details;
  }
}

/**
 * Error 429 - Rate limit
 */
export class RateLimitError extends AppError {
  constructor(message = 'Demasiadas solicitudes', retryAfter = 60, publicMessage = null) {
    super(message, 429, ERROR_TYPES.RATE_LIMIT, publicMessage);
    this.retryAfter = retryAfter;
  }
}

// ============================================
// MIDDLEWARE PARA RUTAS NO ENCONTRADAS
// ============================================

/**
 * @desc    Manejador para rutas no encontradas (404)
 */
export const notFoundHandler = (req, res, next) => {
  const error = new NotFoundError(
    `Ruta no encontrada: ${req.method} ${req.url}`,
    'La página que buscas no existe'
  );
  next(error);
};

/**
 * @desc    Manejador para errores asíncronos (wrapper)
 * @param   {Function} fn - Función asíncrona
 * @returns {Function}
 */
export const asyncHandler = (fn) => {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

// ============================================
// MIDDLEWARE DE VALIDACIÓN DE ERRORES
// ============================================

/**
 * @desc    Valida que no haya errores de sintaxis en JSON
 */
export const jsonErrorHandler = (err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({
      success: false,
      error: {
        type: ERROR_TYPES.VALIDATION,
        message: 'JSON inválido',
        publicMessage: 'El formato de los datos enviados es incorrecto'
      }
    });
  }
  next(err);
};