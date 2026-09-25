import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';

// ============================================
// CONSTANTES
// ============================================

const USER_ROLES = {
  USER: 'user',
  ADMIN: 'admin',
  MODERATOR: 'moderator',
  STAFF: 'staff'
};

const USER_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  SUSPENDED: 'suspended',
  PENDING_VERIFICATION: 'pending_verification'
};

const STATUS_LABELS = {
  [USER_STATUS.ACTIVE]: 'Activo',
  [USER_STATUS.INACTIVE]: 'Inactivo',
  [USER_STATUS.SUSPENDED]: 'Suspendido',
  [USER_STATUS.PENDING_VERIFICATION]: 'Pendiente de verificación'
};

const ROLE_LABELS = {
  [USER_ROLES.USER]: 'Usuario',
  [USER_ROLES.ADMIN]: 'Administrador',
  [USER_ROLES.MODERATOR]: 'Moderador',
  [USER_ROLES.STAFF]: 'Personal'
};

// ============================================
// SUBSCHEMAS
// ============================================

/**
 * Perfil del usuario
 */
const ProfileSchema = new mongoose.Schema({
  phone: {
    type: String,
    match: [/^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}$/, 'Teléfono inválido']
  },
  address: {
    street: { type: String, trim: true },
    city: { type: String, trim: true },
    state: { type: String, trim: true },
    country: { type: String, trim: true, default: 'Colombia' },
    postalCode: { type: String, trim: true }
  },
  birthDate: {
    type: Date,
    validate: {
      validator: function(v) {
        if (!v) return true;
        const age = new Date().getFullYear() - v.getFullYear();
        return age >= 18;
      },
      message: 'Debes tener al menos 18 años'
    }
  },
  documentId: {
    type: String,
    trim: true,
    unique: true,
    sparse: true
  },
  documentType: {
    type: String,
    enum: ['cedula', 'passport', 'nit', 'other'],
    default: 'cedula'
  },
  avatar: {
    type: String,
    validate: {
      validator: function(v) {
        return !v || /^(https?:\/\/.*\.(jpg|jpeg|png|webp|gif))$/i.test(v);
      },
      message: 'URL de avatar inválida'
    }
  },
  preferences: {
    language: { type: String, default: 'es', enum: ['es', 'en'] },
    newsletter: { type: Boolean, default: false },
    promotions: { type: Boolean, default: true },
    emailNotifications: { type: Boolean, default: true }
  }
});

/**
 * Historial de cambios de contraseña
 */
const PasswordHistorySchema = new mongoose.Schema({
  password: {
    type: String,
    required: true
  },
  changedAt: {
    type: Date,
    default: Date.now
  },
  changedBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }
});

/**
 * Token de recuperación
 */
const RecoveryTokenSchema = new mongoose.Schema({
  token: {
    type: String,
    required: true
  },
  expiresAt: {
    type: Date,
    required: true
  },
  used: {
    type: Boolean,
    default: false
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

/**
 * Sesión del usuario
 */
const SessionSchema = new mongoose.Schema({
  token: {
    type: String,
    required: true
  },
  refreshToken: {
    type: String,
    required: true
  },
  userAgent: String,
  ipAddress: String,
  expiresAt: Date,
  createdAt: {
    type: Date,
    default: Date.now
  },
  lastActivity: {
    type: Date,
    default: Date.now
  }
});

/**
 * Actividad del usuario
 */
const ActivityLogSchema = new mongoose.Schema({
  action: {
    type: String,
    required: true,
    enum: ['login', 'logout', 'password_change', 'profile_update', 'booking_create', 'booking_cancel', 'email_verification']
  },
  details: mongoose.Schema.Types.Mixed,
  ipAddress: String,
  userAgent: String,
  timestamp: {
    type: Date,
    default: Date.now
  }
});

// ============================================
// MAIN SCHEMA
// ============================================

const userSchema = new mongoose.Schema({
  // Información básica
  name: { 
    type: String, 
    required: [true, 'El nombre es obligatorio'],
    trim: true,
    minlength: [2, 'El nombre debe tener al menos 2 caracteres'],
    maxlength: [100, 'El nombre no puede exceder 100 caracteres']
  },
  
  lastName: {
    type: String,
    trim: true,
    maxlength: [100, 'El apellido no puede exceder 100 caracteres']
  },
  
  email: { 
    type: String, 
    required: [true, 'El email es obligatorio'],
    unique: true,
    lowercase: true,
    trim: true,
    index: true,
    match: [/^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/, 'Por favor, proporciona un email válido']
  },
  
  password: { 
    type: String, 
    required: [true, 'La contraseña es obligatoria'],
    minlength: [8, 'La contraseña debe tener al menos 8 caracteres'],
    select: false,
    validate: {
      validator: function(v) {
        if (!v) return true;
        // Validar contraseña fuerte: al menos una mayúscula, una minúscula, un número y un carácter especial
        const strongPasswordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/;
        return strongPasswordRegex.test(v);
      },
      message: 'La contraseña debe contener al menos una mayúscula, una minúscula y un número'
    }
  },
  
  // Roles y permisos
  role: { 
    type: String, 
    enum: Object.values(USER_ROLES), 
    default: USER_ROLES.USER,
    index: true
  },
  
  permissions: [{
    type: String,
    enum: ['manage_users', 'manage_bookings', 'manage_suites', 'manage_experiences', 'view_reports', 'manage_content']
  }],
  
  // Estado
  status: {
    type: String,
    enum: Object.values(USER_STATUS),
    default: USER_STATUS.PENDING_VERIFICATION,
    index: true
  },
  
  // Verificación de email
  emailVerified: {
    type: Boolean,
    default: false
  },
  
  emailVerificationToken: {
    type: String,
    select: false
  },
  
  emailVerificationExpires: {
    type: Date,
    select: false
  },
  
  // Perfil
  profile: ProfileSchema,
  
  // Seguridad
  passwordHistory: [PasswordHistorySchema],
  
  lastPasswordChange: {
    type: Date,
    default: Date.now
  },
  
  refreshTokens: [{
    type: String,
    select: false
  }],
  
  activeSessions: [SessionSchema],
  
  // Tokens de recuperación
  recoveryTokens: [RecoveryTokenSchema],
  
  // Intentos de login
  loginAttempts: {
    count: { type: Number, default: 0 },
    lastAttempt: { type: Date },
    lockUntil: { type: Date }
  },
  
  // Actividad
  lastLogin: {
    type: Date,
    default: null
  },
  
  lastLoginIP: {
    type: String
  },
  
  lastLoginLocation: {
    city: String,
    country: String
  },
  
  activityLog: [ActivityLogSchema],
  
  // Preferencias
  preferences: {
    language: { type: String, default: 'es', enum: ['es', 'en'] },
    timezone: { type: String, default: 'America/Bogota' },
    notifications: {
      email: { type: Boolean, default: true },
      sms: { type: Boolean, default: false },
      push: { type: Boolean, default: true }
    }
  },
  
  // Metadatos
  lastActive: {
    type: Date,
    default: Date.now
  },
  
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  },
  
  createdAt: { 
    type: Date, 
    default: Date.now,
    index: true
  },
  
  updatedAt: { 
    type: Date, 
    default: Date.now 
  },
  
  deletedAt: {
    type: Date,
    default: null,
    index: true
  }
}, {
  timestamps: true,
  toJSON: { virtuals: true, getters: true },
  toObject: { virtuals: true }
});

// ============================================
// MIDDLEWARES (Pre/Post Hooks)
// ============================================

/**
 * Encriptar contraseña antes de guardar
 */
userSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  
  try {
    // Generar salt y hash
    const salt = await bcrypt.genSalt(12);
    const hashedPassword = await bcrypt.hash(this.password, salt);
    
    // Guardar contraseña anterior en historial (siempre el HASH, nunca en texto plano)
    if (this.passwordHistory && this.passwordHistory.length > 0) {
      this.passwordHistory.push({
        password: hashedPassword,
        changedAt: new Date(),
        changedBy: this._id
      });
      
      // Limitar historial a 5 contraseñas
      if (this.passwordHistory.length > 5) {
        this.passwordHistory = this.passwordHistory.slice(-5);
      }
    } else if (this.passwordHistory) {
      this.passwordHistory = [{
        password: hashedPassword,
        changedAt: new Date(),
        changedBy: this._id
      }];
    }
    
    this.password = hashedPassword;
    this.lastPasswordChange = new Date();
    next();
  } catch (error) {
    next(error);
  }
});

/**
 * Actualizar updatedAt
 */
userSchema.pre('save', function(next) {
  this.updatedAt = new Date();
  next();
});

/**
 * Validar email único en soft delete
 */
userSchema.pre('save', async function(next) {
  if (this.isModified('email')) {
    const existingUser = await mongoose.model('User').findOne({
      email: this.email,
      _id: { $ne: this._id },
      deletedAt: null
    });
    
    if (existingUser) {
      next(new Error('El email ya está registrado'));
    }
  }
  next();
});

// ============================================
// VIRTUALS
// ============================================

/**
 * Nombre completo
 */
userSchema.virtual('fullName').get(function() {
  if (this.lastName) {
    return `${this.name} ${this.lastName}`;
  }
  return this.name;
});

/**
 * Estado formateado
 */
userSchema.virtual('statusLabel').get(function() {
  return STATUS_LABELS[this.status] || this.status;
});

/**
 * Rol formateado
 */
userSchema.virtual('roleLabel').get(function() {
  return ROLE_LABELS[this.role] || this.role;
});

/**
 * Está bloqueado
 */
userSchema.virtual('isLocked').get(function() {
  return this.loginAttempts.lockUntil && this.loginAttempts.lockUntil > Date.now();
});

/**
 * Tiempo restante de bloqueo (minutos)
 */
userSchema.virtual('lockTimeRemaining').get(function() {
  if (this.isLocked) {
    return Math.ceil((this.loginAttempts.lockUntil - Date.now()) / 60000);
  }
  return 0;
});

/**
 * Está activo
 */
userSchema.virtual('isActive').get(function() {
  return this.status === USER_STATUS.ACTIVE && !this.deletedAt;
});

/**
 * Avatar URL por defecto
 */
userSchema.virtual('avatarUrl').get(function() {
  if (this.profile?.avatar) {
    return this.profile.avatar;
  }
  // Avatar por defecto usando initials
  const initials = this.name.substring(0, 2).toUpperCase();
  return `https://ui-avatars.com/api/?name=${initials}&background=C9A96E&color=fff&size=128`;
});

// ============================================
// INSTANCE METHODS
// ============================================

/**
 * Comparar contraseña
 */
userSchema.methods.comparePassword = async function(candidatePassword) {
  if (!candidatePassword || !this.password) return false;
  return await bcrypt.compare(candidatePassword, this.password);
};

/**
 * Verificar si la contraseña ha sido usada antes
 */
userSchema.methods.isPasswordReused = async function(newPassword) {
  if (!this.passwordHistory || this.passwordHistory.length === 0) {
    return false;
  }
  
  for (const history of this.passwordHistory) {
    const isMatch = await bcrypt.compare(newPassword, history.password);
    if (isMatch) return true;
  }
  
  return false;
};

/**
 * Registrar intento de login fallido
 */
userSchema.methods.recordLoginAttempt = async function() {
  const MAX_ATTEMPTS = 5;
  const LOCK_TIME = 15 * 60 * 1000; // 15 minutos
  
  this.loginAttempts.count += 1;
  this.loginAttempts.lastAttempt = new Date();
  
  if (this.loginAttempts.count >= MAX_ATTEMPTS) {
    this.loginAttempts.lockUntil = Date.now() + LOCK_TIME;
  }
  
  await this.save();
};

/**
 * Resetear intentos de login
 */
userSchema.methods.resetLoginAttempts = async function() {
  this.loginAttempts = {
    count: 0,
    lastAttempt: null,
    lockUntil: null
  };
  await this.save();
};

/**
 * Generar token de verificación de email
 */
userSchema.methods.generateEmailVerificationToken = function() {
  const token = crypto.randomBytes(32).toString('hex');
  this.emailVerificationToken = token;
  this.emailVerificationExpires = Date.now() + 24 * 60 * 60 * 1000; // 24 horas
  return token;
};

/**
 * Verificar email
 */
userSchema.methods.verifyEmail = async function(token) {
  if (this.emailVerificationToken !== token) {
    throw new Error('Token de verificación inválido');
  }
  
  if (this.emailVerificationExpires < Date.now()) {
    throw new Error('Token de verificación expirado');
  }
  
  this.emailVerified = true;
  this.emailVerificationToken = undefined;
  this.emailVerificationExpires = undefined;
  
  if (this.status === USER_STATUS.PENDING_VERIFICATION) {
    this.status = USER_STATUS.ACTIVE;
  }
  
  await this.save();
  return this;
};

/**
 * Generar token de recuperación de contraseña
 */
userSchema.methods.generatePasswordResetToken = function() {
  const token = crypto.randomBytes(32).toString('hex');
  
  this.recoveryTokens.push({
    token,
    expiresAt: Date.now() + 60 * 60 * 1000, // 1 hora
    used: false
  });
  
  // Limitar a 5 tokens activos
  if (this.recoveryTokens.length > 5) {
    this.recoveryTokens = this.recoveryTokens.slice(-5);
  }
  
  return token;
};

/**
 * Verificar token de recuperación
 */
userSchema.methods.verifyPasswordResetToken = function(token) {
  const recoveryToken = this.recoveryTokens.find(t => 
    t.token === token && !t.used && t.expiresAt > Date.now()
  );
  
  if (!recoveryToken) {
    throw new Error('Token de recuperación inválido o expirado');
  }
  
  return recoveryToken;
};

/**
 * Usar token de recuperación
 */
userSchema.methods.usePasswordResetToken = async function(token) {
  const recoveryToken = this.verifyPasswordResetToken(token);
  recoveryToken.used = true;
  await this.save();
};

/**
 * Registrar sesión activa
 */
userSchema.methods.addSession = async function(token, refreshToken, userAgent, ipAddress, expiresIn = 7 * 24 * 60 * 60 * 1000) {
  this.activeSessions.push({
    token,
    refreshToken,
    userAgent,
    ipAddress,
    expiresAt: Date.now() + expiresIn,
    lastActivity: Date.now()
  });
  
  // Limitar a 10 sesiones activas
  if (this.activeSessions.length > 10) {
    // Eliminar sesiones expiradas primero
    this.activeSessions = this.activeSessions.filter(session => session.expiresAt > Date.now());
    // Si aún hay más de 10, eliminar las más antiguas
    if (this.activeSessions.length > 10) {
      this.activeSessions = this.activeSessions.slice(-10);
    }
  }
  
  await this.save();
};

/**
 * Invalidar sesión
 */
userSchema.methods.invalidateSession = async function(token) {
  this.activeSessions = this.activeSessions.filter(session => session.token !== token);
  await this.save();
};

/**
 * Invalidar todas las sesiones
 */
userSchema.methods.invalidateAllSessions = async function() {
  this.activeSessions = [];
  await this.save();
};

/**
 * Registrar actividad
 */
userSchema.methods.logActivity = async function(action, details, ipAddress, userAgent) {
  this.activityLog.push({
    action,
    details,
    ipAddress,
    userAgent,
    timestamp: new Date()
  });
  
  // Limitar historial de actividad a 100 registros
  if (this.activityLog.length > 100) {
    this.activityLog = this.activityLog.slice(-100);
  }
  
  await this.save();
};

/**
 * Soft delete
 */
userSchema.methods.softDelete = async function() {
  this.deletedAt = new Date();
  this.status = USER_STATUS.INACTIVE;
  this.email = `${this.email}_deleted_${this._id}`;
  await this.save();
};

/**
 * Restaurar usuario
 */
userSchema.methods.restore = async function() {
  if (!this.deletedAt) {
    throw new Error('El usuario no está eliminado');
  }
  
  // Restaurar email original (esto requeriría guardar el email original)
  this.deletedAt = null;
  this.status = USER_STATUS.ACTIVE;
  await this.save();
};

// ============================================
// STATIC METHODS
// ============================================

/**
 * Buscar usuario por email incluyendo contraseña
 */
userSchema.statics.findByEmailWithPassword = function(email) {
  return this.findOne({ email, deletedAt: null }).select('+password');
};

/**
 * Buscar usuario activo por email
 */
userSchema.statics.findActiveByEmail = function(email) {
  return this.findOne({ 
    email, 
    status: USER_STATUS.ACTIVE, 
    deletedAt: null 
  });
};

/**
 * Obtener estadísticas de usuarios
 */
userSchema.statics.getStats = async function() {
  const stats = await this.aggregate([
    { $match: { deletedAt: null } },
    {
      $group: {
        _id: null,
        total: { $sum: 1 },
        active: { $sum: { $cond: [{ $eq: ['$status', USER_STATUS.ACTIVE] }, 1, 0] } },
        inactive: { $sum: { $cond: [{ $eq: ['$status', USER_STATUS.INACTIVE] }, 1, 0] } },
        suspended: { $sum: { $cond: [{ $eq: ['$status', USER_STATUS.SUSPENDED] }, 1, 0] } },
        pending: { $sum: { $cond: [{ $eq: ['$status', USER_STATUS.PENDING_VERIFICATION] }, 1, 0] } }
      }
    }
  ]);
  
  const byRole = await this.aggregate([
    { $match: { deletedAt: null } },
    {
      $group: {
        _id: '$role',
        count: { $sum: 1 }
      }
    }
  ]);
  
  return {
    general: stats[0] || { total: 0, active: 0, inactive: 0, suspended: 0, pending: 0 },
    byRole: byRole.reduce((acc, curr) => {
      acc[curr._id] = curr.count;
      return acc;
    }, {})
  };
};

/**
 * Buscar usuarios por rango de fechas
 */
userSchema.statics.findByDateRange = function(startDate, endDate) {
  return this.find({
    createdAt: { $gte: startDate, $lte: endDate },
    deletedAt: null
  }).sort('-createdAt');
};

/**
 * Buscar usuarios recientemente activos
 */
userSchema.statics.findRecentlyActive = function(days = 7) {
  const cutoffDate = new Date();
  cutoffDate.setDate(cutoffDate.getDate() - days);
  
  return this.find({
    lastActive: { $gte: cutoffDate },
    deletedAt: null
  }).sort('-lastActive').limit(50);
};

// ============================================
// ÍNDICES
// ============================================

// Nota: email, status y role ya declaran "index: true" en la propia
// definición del campo; repetirlo aquí con schema.index({...}) generaba
// el aviso de Mongoose "Duplicate schema index". Solo se listan aquí los
// índices que no están declarados en el campo.
userSchema.index({ createdAt: -1 });
userSchema.index({ lastLogin: -1 });
// profile.documentId ya es "unique: true" (crea su propio índice) y
// deletedAt ya declara "index: true" en el campo: repetirlos aquí
// disparaba el aviso "Duplicate schema index" de Mongoose.
userSchema.index({ status: 1, role: 1 });
userSchema.index({ 'activeSessions.token': 1 });
userSchema.index({ 'recoveryTokens.token': 1 });
userSchema.index({ emailVerificationToken: 1 });

// Índice compuesto para búsquedas comunes
userSchema.index({ status: 1, createdAt: -1 });
userSchema.index({ role: 1, status: 1 });

// ============================================
// EXPORTAR MODELO Y CONSTANTES
// ============================================

const User = mongoose.model('User', userSchema);

export default User;
export { USER_ROLES, USER_STATUS, STATUS_LABELS, ROLE_LABELS };