import User from '../models/User.js';
import Booking from '../models/Booking.js';
import jwt from 'jsonwebtoken';
import Joi from 'joi';
import { sendPasswordResetEmail, sendVerificationEmail } from '../utils/email.js';

// ============================================
// VALIDATION SCHEMAS (MEJORADOS)
// ============================================

const registerSchema = Joi.object({
  name: Joi.string()
    .min(2)
    .max(100)
    .required()
    .trim()
    .pattern(/^[a-zA-ZáéíóúñÁÉÍÓÚÑ\s]+$/)
    .messages({
      'string.pattern.base': 'El nombre solo puede contener letras y espacios',
      'string.min': 'El nombre debe tener al menos 2 caracteres',
      'string.max': 'El nombre no puede exceder 100 caracteres',
      'any.required': 'El nombre es obligatorio'
    }),
  
  email: Joi.string()
    .email()
    .required()
    .lowercase()
    .trim()
    .messages({
      'string.email': 'Debe proporcionar un email válido',
      'any.required': 'El email es obligatorio'
    }),
  
  password: Joi.string()
    .min(8)
    .max(100)
    .required()
    .pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/)
    .messages({
      'string.pattern.base': 'La contraseña debe tener al menos 8 caracteres, incluyendo una mayúscula, una minúscula y un número',
      'string.min': 'La contraseña debe tener al menos 8 caracteres',
      'string.max': 'La contraseña no puede exceder 100 caracteres',
      'any.required': 'La contraseña es obligatoria'
    })
});

const loginSchema = Joi.object({
  email: Joi.string()
    .email()
    .required()
    .lowercase()
    .trim()
    .messages({
      'string.email': 'Debe proporcionar un email válido',
      'string.empty': 'El email es obligatorio',
      'any.required': 'El email es obligatorio'
    }),
  
  password: Joi.string()
    .required()
    .min(1)
    .messages({
      'any.required': 'La contraseña es obligatoria',
      'string.empty': 'La contraseña es obligatoria'
    })
});

// ============================================
// HELPER FUNCTIONS
// ============================================

const generateToken = (userId, role) => {
  if (!process.env.JWT_SECRET) {
    throw new Error('JWT_SECRET no está configurado en variables de entorno');
  }
  
  return jwt.sign(
    { id: userId, role },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
  );
};

const sanitizeUser = (user) => {
  return {
    id: user._id,
    name: user.name,
    email: user.email,
    role: user.role,
    createdAt: user.createdAt
  };
};

const logError = (context, error) => {
  console.error(`[ERROR] ${context}:`, {
    message: error.message,
    stack: process.env.NODE_ENV === 'development' ? error.stack : undefined,
    timestamp: new Date().toISOString()
  });
};

// ============================================
// CONTROLLERS
// ============================================

/**
 * @desc    Registrar un nuevo usuario
 * @route   POST /api/auth/register
 * @access  Public
 */
export const register = async (req, res) => {
  try {
    // 1. Validar datos de entrada
    const { error, value } = registerSchema.validate(req.body);
    if (error) {
      return res.status(400).json({
        success: false,
        message: error.details[0].message,
        errors: error.details.map(d => d.message)
      });
    }

    const { name, email, password } = value;
    
    // 2. Verificar si el usuario ya existe
    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(409).json({
        success: false,
        message: 'El email ya está registrado. Por favor, inicia sesión o usa otro email.'
      });
    }
    
    // 3. Crear nuevo usuario
    // Nota: el proyecto no cuenta con un proveedor de email obligatorio
    // configurado. Exigir 'pending_verification' como estado inicial (el
    // valor por defecto del esquema) dejaba a todo usuario nuevo bloqueado
    // en la primera petición autenticada, porque authMiddleware exige
    // status === 'active'. Activamos la cuenta de inmediato y, si hay SMTP
    // configurado, enviamos igualmente un correo de verificación opcional.
    const user = new User({
      name,
      email,
      password,
      status: 'active',
      createdAt: new Date()
    });

    const verifyToken = user.generateEmailVerificationToken();

    await user.save();

    sendVerificationEmail(user.email, user.name, verifyToken).catch(err => {
      console.error('[Register] No se pudo enviar el email de verificación:', err.message);
    });
    
    // 4. Generar token
    const token = generateToken(user._id, user.role);
    
    // 5. Responder con datos seguros
    res.status(201).json({
      success: true,
      message: 'Usuario registrado exitosamente',
      token,
      user: sanitizeUser(user)
    });
    
  } catch (error) {
    logError('Register controller', error);
    
    // Manejo específico para error de duplicado de MongoDB
    if (error.code === 11000) {
      return res.status(409).json({
        success: false,
        message: 'El email ya está registrado'
      });
    }
    
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor. Por favor, intenta más tarde.'
    });
  }
};

/**
 * @desc    Iniciar sesión
 * @route   POST /api/auth/login
 * @access  Public
 */
export const login = async (req, res) => {
  try {
    // 1. Validar datos de entrada
    const { error, value } = loginSchema.validate(req.body);
    if (error) {
      return res.status(400).json({
        success: false,
        message: error.details[0].message,
        errors: error.details.map(d => d.message)
      });
    }
    
    const { email, password } = value;
    
    // 2. Buscar usuario por email (incluir password para comparar)
    const user = await User.findOne({ email, deletedAt: null }).select('+password');
    
    if (!user) {
      req.recordFailedAttempt?.();
      return res.status(401).json({
        success: false,
        message: 'Credenciales inválidas. Verifica tu email y contraseña.'
      });
    }
    
    // 3. Verificar contraseña
    const isMatch = await user.comparePassword(password);
    if (!isMatch) {
      req.recordFailedAttempt?.();
      return res.status(401).json({
        success: false,
        message: 'Credenciales inválidas. Verifica tu email y contraseña.'
      });
    }
    
    // 4. Verificar si la cuenta está activa
    if (user.status === 'inactive' || user.status === 'suspended') {
      return res.status(403).json({
        success: false,
        message: user.status === 'suspended'
          ? 'Tu cuenta está suspendida. Por favor, contacta a soporte.'
          : 'Tu cuenta está inactiva. Por favor, contacta a soporte.'
      });
    }
    
    // 5. Intento correcto: limpiar contador de intentos fallidos
    req.clearLoginAttempts?.();
    
    // 6. Actualizar último login
    user.lastLogin = new Date();
    user.lastLoginIP = req.ip;
    await user.save({ validateBeforeSave: false });
    
    // 6. Generar token
    const token = generateToken(user._id, user.role);
    
    // 7. Responder con datos seguros
    res.json({
      success: true,
      message: 'Inicio de sesión exitoso',
      token,
      user: sanitizeUser(user)
    });
    
  } catch (error) {
    logError('Login controller', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor. Por favor, intenta más tarde.'
    });
  }
};

/**
 * @desc    Obtener información del usuario autenticado
 * @route   GET /api/auth/me
 * @access  Private
 */
export const getMe = async (req, res) => {
  try {
    // El middleware de auth ya agregó req.user
    const user = await User.findById(req.user.id).select('-password');
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    res.json({
      success: true,
      user: sanitizeUser(user)
    });
    
  } catch (error) {
    logError('GetMe controller', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Refrescar token de autenticación
 * @route   POST /api/auth/refresh
 * @access  Private
 */
export const refreshToken = async (req, res) => {
  try {
    const user = await User.findById(req.user.id);
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    const newToken = generateToken(user._id, user.role);
    
    res.json({
      success: true,
      token: newToken
    });
    
  } catch (error) {
    logError('RefreshToken controller', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Cerrar sesión (invalidar token en cliente)
 * @route   POST /api/auth/logout
 * @access  Private
 */
export const logout = async (req, res) => {
  // El logout se maneja en el cliente eliminando el token
  // Esta función es solo para registro si se quiere
  res.json({
    success: true,
    message: 'Sesión cerrada exitosamente'
  });
};

/**
 * @desc    Cambiar contraseña
 * @route   POST /api/auth/change-password
 * @access  Private
 */
export const changePassword = async (req, res) => {
  try {
    const { currentPassword, newPassword } = req.body;
    
    // Validar nueva contraseña
    const passwordValidation = Joi.string()
      .min(8)
      .max(100)
      .pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/)
      .validate(newPassword);
      
    if (passwordValidation.error) {
      return res.status(400).json({
        success: false,
        message: passwordValidation.error.details[0].message
      });
    }
    
    const user = await User.findById(req.user.id).select('+password');
    
    const isMatch = await user.comparePassword(currentPassword);
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        message: 'La contraseña actual es incorrecta'
      });
    }
    
    user.password = newPassword;
    await user.save();
    
    res.json({
      success: true,
      message: 'Contraseña actualizada exitosamente'
    });
    
  } catch (error) {
    logError('ChangePassword controller', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

// ============================================
// NUEVAS FUNCIONES PARA EL CONTROLADOR
// ============================================

/**
 * @desc    Solicitar recuperación de contraseña
 * @route   POST /api/auth/forgot-password
 * @access  Public
 */
export const forgotPassword = async (req, res) => {
  try {
    const { email } = req.body;
    
    const user = await User.findOne({ email, deletedAt: null });
    if (!user) {
      // No revelar si el email existe por seguridad
      return res.json({
        success: true,
        message: 'Si el email está registrado, recibirás un enlace de recuperación'
      });
    }
    
    const resetToken = user.generatePasswordResetToken();
    await user.save();
    
    // El envío no bloquea la respuesta (respuesta uniforme, sin filtrar si la
    // cuenta existe), pero un fallo o SMTP ausente queda registrado como
    // error operativo detectable (BUG-007). Sin datos del usuario en el log.
    sendPasswordResetEmail(user.email, user.name, resetToken)
      .then(result => {
        if (!result?.sent) console.error('[ForgotPassword] ALERTA: correo de recuperación NO enviado:', result?.reason);
      })
      .catch(err => {
        console.error('[ForgotPassword] ALERTA: falló el envío del correo de recuperación:', err.message);
      });
    
    // El token NUNCA se registra en logs ni se devuelve por HTTP en ningún
    // entorno (BUG-008). En desarrollo sin SMTP, el correo se imprime en la
    // consola del servidor (ver utils/email.js).
    res.json({
      success: true,
      message: 'Si el email está registrado, recibirás un enlace de recuperación'
    });
    
  } catch (error) {
    console.error('[ForgotPassword Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al procesar la solicitud'
    });
  }
};

/**
 * @desc    Restablecer contraseña con token
 * @route   POST /api/auth/reset-password
 * @access  Public
 */
export const resetPassword = async (req, res) => {
  try {
    const { token, newPassword } = req.body;
    
    // Buscar usuario con token válido
    const user = await User.findOne({
      'recoveryTokens.token': token,
      'recoveryTokens.used': false,
      'recoveryTokens.expiresAt': { $gt: Date.now() },
      deletedAt: null
    });
    
    if (!user) {
      return res.status(400).json({
        success: false,
        message: 'Token inválido o expirado'
      });
    }
    
    // Verificar que la nueva contraseña no sea igual a la anterior
    const isSamePassword = await user.comparePassword(newPassword);
    if (isSamePassword) {
      return res.status(400).json({
        success: false,
        message: 'La nueva contraseña debe ser diferente a la anterior'
      });
    }
    
    // Actualizar contraseña
    user.password = newPassword;
    await user.usePasswordResetToken(token);
    await user.save();
    
    // Invalidar todas las sesiones activas
    await user.invalidateAllSessions();
    
    // Registrar actividad
    await user.logActivity('password_reset', { success: true }, req.ip, req.headers['user-agent']);
    
    res.json({
      success: true,
      message: 'Contraseña actualizada exitosamente'
    });
    
  } catch (error) {
    console.error('[ResetPassword Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al restablecer la contraseña'
    });
  }
};

/**
 * @desc    Verificar email
 * @route   GET /api/auth/verify-email/:token
 * @access  Public
 */
export const verifyEmail = async (req, res) => {
  try {
    const { token } = req.params;
    
    const user = await User.findOne({
      emailVerificationToken: token,
      emailVerificationExpires: { $gt: Date.now() },
      deletedAt: null
    });
    
    if (!user) {
      return res.status(400).json({
        success: false,
        message: 'Token de verificación inválido o expirado'
      });
    }
    
    await user.verifyEmail(token);
    
    res.json({
      success: true,
      message: 'Email verificado exitosamente'
    });
    
  } catch (error) {
    console.error('[VerifyEmail Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al verificar el email'
    });
  }
};

/**
 * @desc    Reenviar email de verificación
 * @route   POST /api/auth/resend-verification
 * @access  Public
 */
export const resendVerificationEmail = async (req, res) => {
  try {
    const { email } = req.body;
    
    const user = await User.findOne({ 
      email, 
      emailVerified: false,
      deletedAt: null 
    });
    
    const genericResponse = {
      success: true,
      message: 'Si el email está registrado y sin verificar, recibirás un nuevo enlace de verificación'
    };
    
    // Respuesta uniforme: no revela si la cuenta existe o ya está verificada
    if (!user) {
      return res.json(genericResponse);
    }
    
    const verifyToken = user.generateEmailVerificationToken();
    await user.save();
    
    sendVerificationEmail(user.email, user.name, verifyToken)
      .then(result => {
        if (!result?.sent) console.error('[ResendVerification] ALERTA: correo NO enviado:', result?.reason);
      })
      .catch(err => {
        console.error('[ResendVerification] ALERTA: falló el envío:', err.message);
      });
    
    res.json(genericResponse);
    
  } catch (error) {
    console.error('[ResendVerification Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al reenviar la verificación'
    });
  }
};

/**
 * @desc    Actualizar perfil del usuario
 * @route   PUT /api/auth/profile
 * @access  Private
 */
export const updateProfile = async (req, res) => {
  try {
    const { name, lastName, phone, address, preferences } = req.body;
    
    const user = await User.findById(req.user.id);
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    // Actualizar campos permitidos
    if (name) user.name = name;
    if (lastName !== undefined) user.lastName = lastName;
    if (phone !== undefined) user.profile.phone = phone;
    if (address) user.profile.address = { ...user.profile.address, ...address };
    if (preferences) user.preferences = { ...user.preferences, ...preferences };
    
    await user.save();
    
    // Registrar actividad
    await user.logActivity('profile_update', { success: true }, req.ip, req.headers['user-agent']);
    
    res.json({
      success: true,
      message: 'Perfil actualizado exitosamente',
      user: {
        id: user._id,
        name: user.name,
        lastName: user.lastName,
        email: user.email,
        profile: user.profile,
        preferences: user.preferences
      }
    });
    
  } catch (error) {
    console.error('[UpdateProfile Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al actualizar el perfil'
    });
  }
};

/**
 * @desc    Obtener estadísticas del perfil
 * @route   GET /api/auth/profile/stats
 * @access  Private
 */
export const getProfileStats = async (req, res) => {
  try {
    
    const [totalBookings, completedBookings, totalSpent] = await Promise.all([
      Booking.countDocuments({ user: req.user.id }),
      Booking.countDocuments({ user: req.user.id, status: 'completed' }),
      Booking.aggregate([
        { $match: { user: req.user.id, status: 'completed' } },
        { $group: { _id: null, total: { $sum: '$totalPrice' } } }
      ])
    ]);
    
    res.json({
      success: true,
      data: {
        memberSince: req.userData.createdAt,
        totalBookings,
        completedBookings,
        totalSpent: totalSpent[0]?.total || 0,
        lastLogin: req.userData.lastLogin,
        emailVerified: req.userData.emailVerified
      }
    });
    
  } catch (error) {
    console.error('[GetProfileStats Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener estadísticas'
    });
  }
};

/**
 * @desc    Obtener todos los usuarios (admin)
 * @route   GET /api/auth/admin/users
 * @access  Private (Admin)
 */
export const getAllUsers = async (req, res) => {
  try {
    const { page = 1, limit = 20, status, role, search } = req.query;
    
    const query = { deletedAt: null };
    if (status) query.status = status;
    if (role) query.role = role;
    if (search) {
      query.$or = [
        { name: { $regex: search, $options: 'i' } },
        { email: { $regex: search, $options: 'i' } }
      ];
    }
    
    const skip = (parseInt(page) - 1) * parseInt(limit);
    
    const [users, total] = await Promise.all([
      User.find(query)
        .select('-password -recoveryTokens -activeSessions')
        .sort('-createdAt')
        .skip(skip)
        .limit(parseInt(limit)),
      User.countDocuments(query)
    ]);
    
    res.json({
      success: true,
      data: users,
      pagination: {
        currentPage: parseInt(page),
        totalPages: Math.ceil(total / parseInt(limit)),
        totalItems: total,
        itemsPerPage: parseInt(limit)
      }
    });
    
  } catch (error) {
    console.error('[GetAllUsers Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener usuarios'
    });
  }
};

/**
 * @desc    Obtener usuario por ID (admin)
 * @route   GET /api/auth/admin/users/:id
 * @access  Private (Admin)
 */
export const getUserById = async (req, res) => {
  try {
    const { id } = req.params;
    
    const user = await User.findById(id)
      .select('-password -recoveryTokens -activeSessions');
    
    if (!user || user.deletedAt) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    // Obtener estadísticas adicionales
    const bookings = await Booking.countDocuments({ user: id });
    
    res.json({
      success: true,
      data: {
        ...user.toObject(),
        stats: { totalBookings: bookings }
      }
    });
    
  } catch (error) {
    console.error('[GetUserById Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener el usuario'
    });
  }
};

/**
 * @desc    Actualizar estado de usuario (admin)
 * @route   PUT /api/auth/admin/users/:id/status
 * @access  Private (Admin)
 */
export const updateUserStatus = async (req, res) => {
  try {
    const { id } = req.params;
    const { status, reason } = req.body;
    
    const user = await User.findById(id);
    
    if (!user || user.deletedAt) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    user.status = status;
    
    if (status === 'suspended') {
      // Invalidar todas las sesiones
      await user.invalidateAllSessions();
    }
    
    await user.save();
    
    // Registrar actividad
    await user.logActivity('status_change', { 
      newStatus: status, 
      reason,
      changedBy: req.user.id 
    }, req.ip, req.headers['user-agent']);
    
    res.json({
      success: true,
      message: `Usuario ${status === 'active' ? 'activado' : status === 'inactive' ? 'desactivado' : 'suspendido'} exitosamente`,
      data: { id: user._id, status: user.status }
    });
    
  } catch (error) {
    console.error('[UpdateUserStatus Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al actualizar el estado del usuario'
    });
  }
};

/**
 * @desc    Eliminar usuario (soft delete)
 * @route   DELETE /api/auth/admin/users/:id
 * @access  Private (Admin)
 */
export const deleteUser = async (req, res) => {
  try {
    const { id } = req.params;
    
    const user = await User.findById(id);
    
    if (!user) {
      return res.status(404).json({
        success: false,
        message: 'Usuario no encontrado'
      });
    }
    
    await user.softDelete();
    
    // Registrar actividad
    await user.logActivity('user_deleted', { 
      deletedBy: req.user.id 
    }, req.ip, req.headers['user-agent']);
    
    res.json({
      success: true,
      message: 'Usuario eliminado exitosamente'
    });
    
  } catch (error) {
    console.error('[DeleteUser Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al eliminar el usuario'
    });
  }
};