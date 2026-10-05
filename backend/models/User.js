import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { isValidEmail, EMAIL_MAX_LENGTH } from '../utils/validators.js';

// ============================================
// CONSTANTES
// ============================================

const USER_ROLES = {
  USER: 'user',
  ADMIN: 'admin',
  HOST: 'host'
};

const USER_STATUS = {
  ACTIVE: 'active',
  INACTIVE: 'inactive',
  SUSPENDED: 'suspended',
  PENDING_VERIFICATION: 'pending_verification'
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
    enum: [
      'login', 'logout', 'password_change', 'profile_update', 'booking_create', 'booking_cancel',
      'email_verification', 'password_reset', 'status_change', 'user_deleted'
    ]
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
    maxlength: [EMAIL_MAX_LENGTH, 'El email no puede exceder 254 caracteres'],
    validate: { validator: isValidEmail, message: 'Por favor, proporciona un email válido' }
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
  
  branches: [{ 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'Branch',
    index: true
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
  lastPasswordChange: {
    type: Date,
    default: Date.now
  },
  
  activeSessions: [SessionSchema],
  
  // Tokens de recuperación
  recoveryTokens: [RecoveryTokenSchema],
  
  // Actividad
  lastLogin: {
    type: Date,
    default: null
  },
  
  lastLoginIP: {
    type: String
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
      return next(new Error('El email ya está registrado'));
    }
  }
  next();
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
  // El historial es auxiliar: si falla NUNCA debe convertir en error una
  // operación que ya se completó (antes, un fallo aquí devolvía 500 después
  // de haber cambiado la contraseña, el estado o eliminado al usuario).
  try {
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
  } catch (err) {
    console.error(`[Activity] No se pudo registrar "${action}":`, err.message);
  }
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
export { USER_ROLES, USER_STATUS };