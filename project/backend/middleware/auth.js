import jwt from 'jsonwebtoken';
import User from '../models/User.js';

// ============================================
// CONFIGURACIÓN
// ============================================

// Almacenamiento en memoria para blacklist de tokens (en producción usar Redis)
const tokenBlacklist = new Map();

// Configuración de rate limiting por usuario
const loginAttempts = new Map();
const MAX_LOGIN_ATTEMPTS = 5;
const LOCKOUT_TIME_MS = 15 * 60 * 1000; // 15 minutos

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Verifica que JWT_SECRET esté configurado
 */
const validateJWTSecret = () => {
  if (!process.env.JWT_SECRET) {
    throw new Error('JWT_SECRET no está configurado en variables de entorno');
  }
  return process.env.JWT_SECRET;
};

/**
 * Log de eventos de seguridad
 */
const securityLog = (event, details) => {
  const logEntry = {
    timestamp: new Date().toISOString(),
    event,
    ...details,
    ip: details.ip || 'unknown',
    userAgent: details.userAgent || 'unknown'
  };
  
  console.log(`[SECURITY] ${JSON.stringify(logEntry)}`);
  
  // En producción, enviar a servicio de logging (Sentry, Datadog, etc.)
  if (process.env.NODE_ENV === 'production') {
    // Aquí integrarías tu servicio de logging preferido
  }
};

/**
 * Verifica si un token está en blacklist
 */
const isTokenBlacklisted = (token) => {
  const blacklisted = tokenBlacklist.get(token);
  if (blacklisted && blacklisted.expiresAt > Date.now()) {
    return true;
  }
  if (blacklisted) {
    tokenBlacklist.delete(token); // Limpiar expirados
  }
  return false;
};

/**
 * Añade token a blacklist (para logout)
 */
export const blacklistToken = (token, expiresIn = 7 * 24 * 60 * 60 * 1000) => {
  tokenBlacklist.set(token, {
    expiresAt: Date.now() + expiresIn
  });
  
  // Limpiar blacklist periódicamente
  if (tokenBlacklist.size > 1000) {
    for (const [key, value] of tokenBlacklist.entries()) {
      if (value.expiresAt < Date.now()) {
        tokenBlacklist.delete(key);
      }
    }
  }
};

/**
 * Verifica rate limiting por email
 */
const checkRateLimit = (email, ip) => {
  const key = `${email}:${ip}`;
  const attempts = loginAttempts.get(key);
  
  if (!attempts) {
    return { allowed: true, remaining: MAX_LOGIN_ATTEMPTS };
  }
  
  if (attempts.count >= MAX_LOGIN_ATTEMPTS) {
    const timeLeft = Math.ceil((attempts.lockUntil - Date.now()) / 1000);
    if (timeLeft > 0) {
      return { 
        allowed: false, 
        remaining: 0, 
        lockUntil: attempts.lockUntil,
        timeLeft
      };
    }
    // Resetear si ya pasó el tiempo de bloqueo
    loginAttempts.delete(key);
    return { allowed: true, remaining: MAX_LOGIN_ATTEMPTS };
  }
  
  return { 
    allowed: true, 
    remaining: MAX_LOGIN_ATTEMPTS - attempts.count 
  };
};

/**
 * Registra intento de login fallido
 */
const recordFailedAttempt = (email, ip) => {
  const key = `${email}:${ip}`;
  const attempts = loginAttempts.get(key);
  
  if (!attempts) {
    loginAttempts.set(key, {
      count: 1,
      firstAttempt: Date.now(),
      lockUntil: null
    });
  } else {
    attempts.count++;
    if (attempts.count >= MAX_LOGIN_ATTEMPTS) {
      attempts.lockUntil = Date.now() + LOCKOUT_TIME_MS;
    }
    loginAttempts.set(key, attempts);
  }
};

/**
 * Limpia intentos de login exitosos
 */
export const clearLoginAttempts = (email, ip) => {
  const key = `${email}:${ip}`;
  loginAttempts.delete(key);
};

// ============================================
// MIDDLEWARE PRINCIPAL
// ============================================

/**
 * @desc    Middleware de autenticación JWT
 * @param   {Object} req - Request object
 * @param   {Object} res - Response object
 * @param   {Function} next - Next middleware
 */
export const authMiddleware = async (req, res, next) => {
  const startTime = Date.now();
  const clientIp = req.ip || req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
  const userAgent = req.headers['user-agent'] || 'unknown';
  
  try {
    // 1. Validar que JWT_SECRET existe
    validateJWTSecret();
    
    // 2. Extraer token del header
    const authHeader = req.header('Authorization');
    let token = null;
    
    if (authHeader && authHeader.startsWith('Bearer ')) {
      token = authHeader.substring(7);
    } else if (authHeader) {
      token = authHeader;
    }
    
    // 3. Verificar que el token existe
    if (!token) {
      securityLog('AUTH_FAILED_NO_TOKEN', {
        ip: clientIp,
        userAgent,
        path: req.path,
        method: req.method
      });
      
      return res.status(401).json({
        success: false,
        message: 'No autorizado. Token no proporcionado.',
        code: 'MISSING_TOKEN'
      });
    }
    
    // 4. Verificar que el token no está en blacklist
    if (isTokenBlacklisted(token)) {
      securityLog('AUTH_FAILED_BLACKLISTED_TOKEN', {
        ip: clientIp,
        userAgent,
        path: req.path
      });
      
      return res.status(401).json({
        success: false,
        message: 'Sesión expirada. Por favor, inicia sesión nuevamente.',
        code: 'TOKEN_BLACKLISTED'
      });
    }
    
    // 5. Verificar y decodificar token
    let decoded;
    try {
      decoded = jwt.verify(token, process.env.JWT_SECRET, {
        algorithms: ['HS256'],
        maxAge: process.env.JWT_EXPIRES_IN || '7d'
      });
    } catch (jwtError) {
      let errorMessage = 'Token inválido';
      let errorCode = 'INVALID_TOKEN';
      
      if (jwtError.name === 'TokenExpiredError') {
        errorMessage = 'Token expirado. Por favor, inicia sesión nuevamente.';
        errorCode = 'TOKEN_EXPIRED';
        securityLog('AUTH_FAILED_EXPIRED_TOKEN', {
          ip: clientIp,
          userAgent,
          expiredAt: jwtError.expiredAt
        });
      } else if (jwtError.name === 'JsonWebTokenError') {
        errorMessage = 'Token inválido o malformado';
        errorCode = 'INVALID_TOKEN_FORMAT';
        securityLog('AUTH_FAILED_INVALID_TOKEN', {
          ip: clientIp,
          userAgent,
          error: jwtError.message
        });
      }
      
      return res.status(401).json({
        success: false,
        message: errorMessage,
        code: errorCode
      });
    }
    
    // 6. Verificar que el usuario aún existe en la BD
    const user = await User.findById(decoded.id).select('-password');
    if (!user) {
      securityLog('AUTH_FAILED_USER_NOT_FOUND', {
        ip: clientIp,
        userId: decoded.id
      });
      
      return res.status(401).json({
        success: false,
        message: 'Usuario no encontrado. Por favor, regístrate nuevamente.',
        code: 'USER_NOT_FOUND'
      });
    }
    
    // 7. Verificar que la cuenta está activa
    if (user.status !== 'active') {
      securityLog('AUTH_FAILED_INACTIVE_ACCOUNT', {
        ip: clientIp,
        userId: user._id,
        status: user.status
      });
      
      let message = 'Cuenta inactiva';
      if (user.status === 'suspended') {
        message = 'Cuenta suspendida. Por favor, contacta a soporte.';
      } else if (user.status === 'inactive') {
        message = 'Cuenta inactiva. Por favor, verifica tu email o contacta a soporte.';
      }
      
      return res.status(403).json({
        success: false,
        message,
        code: `ACCOUNT_${user.status.toUpperCase()}`
      });
    }
    
    // 8. Verificar que el token no fue emitido ANTES del último cambio de
    // contraseña. Sin esto, resetPassword()/changePassword() llaman a
    // invalidateAllSessions() pero eso solo vacía `activeSessions` en Mongo,
    // un array que este middleware nunca consulta: un JWT robado seguía
    // siendo válido hasta expirar (hasta 7 días) aunque la víctima ya
    // hubiera "recuperado" su cuenta cambiando la contraseña.
    if (user.lastPasswordChange && decoded.iat) {
      const passwordChangedAtSeconds = Math.floor(new Date(user.lastPasswordChange).getTime() / 1000);
      if (decoded.iat < passwordChangedAtSeconds) {
        securityLog('AUTH_FAILED_TOKEN_ISSUED_BEFORE_PASSWORD_CHANGE', {
          ip: clientIp,
          userId: user._id,
          tokenIat: decoded.iat,
          passwordChangedAt: passwordChangedAtSeconds
        });

        return res.status(401).json({
          success: false,
          message: 'Tu sesión ya no es válida porque la contraseña fue cambiada. Por favor, inicia sesión nuevamente.',
          code: 'TOKEN_ISSUED_BEFORE_PASSWORD_CHANGE'
        });
      }
    }

    // 9. El rol SIEMPRE se toma de la base de datos, nunca del token.
    // Antes se usaba decoded.role: si a alguien le quitabas el rol de
    // admin, su token viejo seguía funcionando como admin hasta que
    // expirara (hasta 7 días), porque solo se registraba el desajuste en
    // el log de seguridad sin actuar sobre él.
    if (decoded.role !== user.role) {
      securityLog('AUTH_ROLE_MISMATCH_CORRECTED', {
        ip: clientIp,
        userId: user._id,
        tokenRole: decoded.role,
        dbRole: user.role
      });
    }

    // 10. Adjuntar información del usuario a la request
    req.user = {
      id: decoded.id,
      role: user.role,
      ...(user.role === 'admin' && { isAdmin: true })
    };
    req.userData = user;
    
    // 11. Log de acceso exitoso (solo en desarrollo o para endpoints críticos)
    if (process.env.NODE_ENV === 'development' || req.path.includes('/admin')) {
      securityLog('AUTH_SUCCESS', {
        ip: clientIp,
        userId: user._id,
        role: user.role,
        path: req.path,
        method: req.method,
        responseTime: Date.now() - startTime
      });
    }
    
    next();
    
  } catch (error) {
    console.error('[Auth Middleware Error]:', error);
    
    securityLog('AUTH_MIDDLEWARE_ERROR', {
      ip: clientIp,
      error: error.message,
      stack: process.env.NODE_ENV === 'development' ? error.stack : undefined
    });
    
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor al verificar autenticación',
      code: 'AUTH_SERVER_ERROR'
    });
  }
};

/**
 * @desc    Middleware de autenticación opcional (no requiere token)
 * @param   {Object} req - Request object
 * @param   {Object} res - Response object
 * @param   {Function} next - Next middleware
 */
export const optionalAuthMiddleware = async (req, res, next) => {
  try {
    const authHeader = req.header('Authorization');
    let token = null;
    
    if (authHeader && authHeader.startsWith('Bearer ')) {
      token = authHeader.substring(7);
    }
    
    if (token && !isTokenBlacklisted(token)) {
      try {
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        const user = await User.findById(decoded.id).select('-password');
        if (user && user.status === 'active') {
          req.user = {
            id: decoded.id,
            role: user.role
          };
          req.userData = user;
        }
      } catch (error) {
        // Token inválido, ignorar
      }
    }
    
    next();
  } catch (error) {
    next();
  }
};

/**
 * @desc    Middleware de autorización de administrador
 * @param   {Object} req - Request object
 * @param   {Object} res - Response object
 * @param   {Function} next - Next middleware
 */
export const adminMiddleware = (req, res, next) => {
  const clientIp = req.ip || req.headers['x-forwarded-for'] || 'unknown';
  
  // Verificar que el usuario existe en la request
  if (!req.user) {
    securityLog('ADMIN_DENIED_NO_USER', {
      ip: clientIp,
      path: req.path
    });
    
    return res.status(401).json({
      success: false,
      message: 'Autenticación requerida para acceder a esta ruta',
      code: 'AUTH_REQUIRED'
    });
  }
  
  // Verificar rol de administrador
  if (req.user.role !== 'admin') {
    securityLog('ADMIN_DENIED_INSUFFICIENT_ROLE', {
      ip: clientIp,
      userId: req.user.id,
      role: req.user.role,
      path: req.path
    });
    
    return res.status(403).json({
      success: false,
      message: 'Acceso denegado. Se requieren permisos de administrador.',
      code: 'INSUFFICIENT_PERMISSIONS'
    });
  }
  
  // Log de acceso de administrador
  securityLog('ADMIN_ACCESS', {
    ip: clientIp,
    userId: req.user.id,
    path: req.path,
    method: req.method
  });
  
  next();
};

/**
 * @desc    Middleware para verificar permisos específicos
 * @param   {string[]} allowedRoles - Lista de roles permitidos
 * @returns {Function}
 */
export const roleMiddleware = (allowedRoles) => {
  return (req, res, next) => {
    const clientIp = req.ip || req.headers['x-forwarded-for'] || 'unknown';
    
    if (!req.user) {
      return res.status(401).json({
        success: false,
        message: 'Autenticación requerida',
        code: 'AUTH_REQUIRED'
      });
    }
    
    if (!allowedRoles.includes(req.user.role)) {
      securityLog('ROLE_DENIED', {
        ip: clientIp,
        userId: req.user.id,
        role: req.user.role,
        allowedRoles,
        path: req.path
      });
      
      return res.status(403).json({
        success: false,
        message: `Acceso denegado. Se requieren los siguientes roles: ${allowedRoles.join(', ')}`,
        code: 'INSUFFICIENT_PERMISSIONS'
      });
    }
    
    next();
  };
};

/**
 * @desc    Middleware de rate limiting para login
 * @param   {Object} req - Request object
 * @param   {Object} res - Response object
 * @param   {Function} next - Next middleware
 */
export const loginRateLimiter = (req, res, next) => {
  const { email } = req.body;
  const clientIp = req.ip || req.headers['x-forwarded-for'] || 'unknown';
  
  if (!email) {
    return next();
  }
  
  const rateLimit = checkRateLimit(email, clientIp);
  
  if (!rateLimit.allowed) {
    securityLog('LOGIN_RATE_LIMIT_EXCEEDED', {
      ip: clientIp,
      email,
      lockUntil: new Date(rateLimit.lockUntil).toISOString()
    });
    
    return res.status(429).json({
      success: false,
      message: `Demasiados intentos fallidos. Intenta nuevamente en ${rateLimit.timeLeft} segundos.`,
      code: 'RATE_LIMIT_EXCEEDED',
      retryAfter: rateLimit.timeLeft
    });
  }
  
  // Adjuntar información de rate limit a la request
  req.rateLimit = rateLimit;
  req.recordFailedAttempt = () => recordFailedAttempt(email, clientIp);
  req.clearLoginAttempts = () => clearLoginAttempts(email, clientIp);
  
  next();
};

// ============================================
// EXPORTAR FUNCIONES ADICIONALES
// ============================================

export const getTokenBlacklistSize = () => tokenBlacklist.size;
export const getLoginAttemptsSize = () => loginAttempts.size;