import mongoose from 'mongoose';
import { isValidEmail, EMAIL_MAX_LENGTH } from '../utils/validators.js';
import SuiteNight from './SuiteNight.js';
import { holdDeadline } from '../utils/bookingRules.js';
import { toCalendarDate, todayCalendarDate, calculateNights, eachNight } from '../utils/dates.js';

// ============================================
// CONSTANTES
// ============================================

const BOOKING_STATUS = {
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  CHECKED_IN: 'checked_in',
  CHECKED_OUT: 'checked_out',
  CANCELLED: 'cancelled',
  COMPLETED: 'completed',
  NO_SHOW: 'no_show',
  EXPIRED: 'expired' // no se pagó a tiempo: libera las noches
};

const PAYMENT_STATUS = {
  PENDING: 'pending',
  PAID: 'paid',
  FAILED: 'failed',
  REFUNDED: 'refunded',
  PARTIAL: 'partial'
};

const STATUS_TRANSITIONS = {
  [BOOKING_STATUS.PENDING]: [BOOKING_STATUS.CONFIRMED, BOOKING_STATUS.CANCELLED, BOOKING_STATUS.EXPIRED],
  [BOOKING_STATUS.CONFIRMED]: [BOOKING_STATUS.CHECKED_IN, BOOKING_STATUS.COMPLETED, BOOKING_STATUS.CANCELLED, BOOKING_STATUS.NO_SHOW],
  [BOOKING_STATUS.CHECKED_IN]: [BOOKING_STATUS.CHECKED_OUT, BOOKING_STATUS.COMPLETED],
  [BOOKING_STATUS.CHECKED_OUT]: [],
  [BOOKING_STATUS.CANCELLED]: [],
  [BOOKING_STATUS.COMPLETED]: [],
  [BOOKING_STATUS.NO_SHOW]: [],
  [BOOKING_STATUS.EXPIRED]: []
};

// Única fuente de verdad de los estados que BLOQUEAN noches (BUG-005).
// "paid" NO es un estado de reserva: pertenece a paymentStatus.
const BLOCKING_BOOKING_STATUSES = [BOOKING_STATUS.PENDING, BOOKING_STATUS.CONFIRMED, BOOKING_STATUS.CHECKED_IN];

const PAYMENT_STATUS_TRANSITIONS = {
  [PAYMENT_STATUS.PENDING]: [PAYMENT_STATUS.PAID, PAYMENT_STATUS.FAILED, PAYMENT_STATUS.PARTIAL],
  [PAYMENT_STATUS.PAID]: [PAYMENT_STATUS.REFUNDED, PAYMENT_STATUS.PARTIAL],
  [PAYMENT_STATUS.FAILED]: [PAYMENT_STATUS.PENDING, PAYMENT_STATUS.PAID, PAYMENT_STATUS.PARTIAL],
  [PAYMENT_STATUS.REFUNDED]: [],
  [PAYMENT_STATUS.PARTIAL]: [PAYMENT_STATUS.PAID, PAYMENT_STATUS.REFUNDED]
};

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Normaliza a fecha de calendario (medianoche UTC, día según la zona
 * horaria del hotel). Ver utils/dates.js (CONS-014).
 */
const normalizeDate = (date) => toCalendarDate(date);

// ============================================
// SCHEMA DEFINITION
// ============================================

const bookingSchema = new mongoose.Schema({
  // Relaciones
  user: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'User', 
    required: [true, 'El usuario es obligatorio'],
    index: true
  },
  suite: { 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'Suite', 
    required: [true, 'La suite es obligatoria'],
    index: true
  },
  // Sucursal y habitación física asignada (slot 1..totalUnits de la suite).
  // Se copian al reservar: la sucursal de una reserva no cambia aunque luego
  // se edite la suite.
  branch: { type: mongoose.Schema.Types.ObjectId, ref: 'Branch', index: true },
  unitSlot: { type: Number, min: 1, default: 1 },
  
  // Fechas
  checkIn: { 
    type: Date, 
    required: [true, 'La fecha de check-in es obligatoria'],
    validate: {
      validator: function(v) {
        // Solo al crear o cuando se cambia la fecha: una reserva en curso
        // debe poder cancelarse/cobrarse aunque su check-in ya sea pasado.
        if (!this.isNew && !this.isModified('checkIn')) return true;
        return normalizeDate(v) >= todayCalendarDate();
      },
      message: 'La fecha de check-in no puede ser anterior a hoy'
    }
  },
  checkOut: { 
    type: Date, 
    required: [true, 'La fecha de check-out es obligatoria'],
    validate: {
      validator: function(v) {
        return v > this.checkIn;
      },
      message: 'La fecha de check-out debe ser posterior al check-in'
    }
  },
  
  // Huéspedes
  guests: { 
    type: Number, 
    required: [true, 'El número de huéspedes es obligatorio'],
    min: [1, 'Mínimo 1 huésped'],
    max: [20, 'Máximo 20 huéspedes']
  },
  
  // De `guests` (total), cuántos son niños. Adultos = guests - children (mínimo 1).
  children: { type: Number, default: 0, min: [0, 'Niños inválido'], max: [10, 'Máximo 10 niños'] },
  
  // Experiencias
  experiences: [{ 
    type: mongoose.Schema.Types.ObjectId, 
    ref: 'Experience' 
  }],
  
  // Precios
  subtotal: { 
    type: Number, 
    min: 0,
    default: 0
  },
  experiencesTotal: {
    type: Number,
    min: 0,
    default: 0
  },
  discount: {
    type: Number,
    min: 0,
    default: 0
  },
  discountReason: {
    type: String,
    default: null
  },
  // Desglose guardado al cotizar: lo prometido al huésped no cambia aunque
  // luego cambien las temporadas o las promociones.
  pricing: {
    discountType: { type: String, enum: ['promotion', 'long_stay', null], default: null },
    nights: [{
      _id: false,
      date: { type: Date, required: true },
      season: { type: String, enum: ['low', 'mid', 'high', 'peak'] },
      price: { type: Number, min: 0, required: true },
      promoPercent: { type: Number, min: 0, max: 100, default: 0 },
      promotionId: { type: mongoose.Schema.Types.ObjectId, ref: 'Promotion' },
      promotionTitle: String
    }]
  },
  totalPrice: { 
    type: Number, 
    required: [true, 'El precio total es obligatorio'],
    min: [0, 'El precio total no puede ser negativo']
  },
  
  // Retención: mientras la reserva está pendiente, las noches se guardan hasta
  // esta hora; si no se paga, el barrendero (services/bookingExpiry.js) la
  // pasa a "expired" y libera las noches.
  holdExpiresAt: { type: Date, index: true },
  expiredAt: { type: Date },
  
  // Estados
  status: { 
    type: String, 
    enum: Object.values(BOOKING_STATUS), 
    default: BOOKING_STATUS.PENDING,
    index: true
  },
  paymentStatus: {
    type: String,
    enum: Object.values(PAYMENT_STATUS),
    default: PAYMENT_STATUS.PENDING,
    index: true
  },
  
  // Datos del huésped
  guestName: { 
    type: String, 
    required: [true, 'El nombre del huésped es obligatorio'],
    trim: true,
    minlength: [2, 'El nombre debe tener al menos 2 caracteres'],
    maxlength: [100, 'El nombre no puede exceder 100 caracteres']
  },
  guestEmail: { 
    type: String, 
    required: [true, 'El email del huésped es obligatorio'],
    lowercase: true,
    trim: true,
    maxlength: [EMAIL_MAX_LENGTH, 'El email no puede exceder 254 caracteres'],
    validate: { validator: isValidEmail, message: 'Email inválido' }
  },
  guestPhone: { 
    type: String,
    match: [/^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}$/, 'Teléfono inválido']
  },
  
  // Información adicional
  specialRequests: { 
    type: String,
    maxlength: [500, 'Las solicitudes especiales no pueden exceder 500 caracteres']
  },
  
  // Pagos
  transactionId: { 
    type: String,
    unique: true,
    sparse: true
  },
  paymentMethod: {
    type: String,
    enum: ['credit_card', 'debit_card', 'paypal', 'bank_transfer', 'cash', 'card', 'pse', 'nequi', null],
    default: null
  },
  paidAt: { type: Date },
  // Importes efectivos (BUG-003): el reembolso se calcula sobre lo cobrado
  amountPaid: { type: Number, min: 0, default: 0 },
  amountRefunded: { type: Number, min: 0, default: 0 },
  
  // Cancelación
  cancelledAt: { type: Date },
  cancellationDetails: {
    reason: { type: String, maxlength: 500 },
    cancellationFee: { type: Number, default: 0 },
    refundAmount: { type: Number, default: 0 },
    // none = nada que devolver | pending = falta ejecutar la devolución | completed = devuelto
    refundStatus: { type: String, enum: ['none', 'pending', 'completed'], default: 'none' },
    cancelledBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    cancelledAt: { type: Date, default: Date.now }
  },
  
  // Modificaciones
  modifiedAt: { type: Date },
  modificationHistory: [{
    date: { type: Date, default: Date.now },
    oldCheckIn: Date,
    oldCheckOut: Date,
    oldTotalPrice: Number,
    newCheckIn: Date,
    newCheckOut: Date,
    newTotalPrice: Number,
    priceDifference: Number,
    addedNights: { type: Number, default: 0 },
    extraChargeExpiresAt: Date,
    modifiedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' }
  }],
  
  // Metadatos
  bookingDate: { type: Date, default: Date.now },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now },
  
  // Notas internas (solo admin)
  internalNotes: {
    type: String,
    maxlength: 1000,
    select: false // No se devuelve por defecto
  }
}, {
  timestamps: { createdAt: 'createdAt', updatedAt: 'updatedAt' },
  toJSON: { virtuals: true, getters: true },
  toObject: { virtuals: true }
});

// ============================================
// MIDDLEWARES (Pre/Post Hooks)
// ============================================

/**
 * Recuerda el estado con el que se cargó el documento, para validar
 * transiciones al guardar. (Antes se guardaba en el ÚLTIMO pre-save, ya con
 * el valor nuevo, así que las transiciones inválidas nunca se detectaban.)
 */
const rememberOriginalStates = (doc) => {
  doc._originalStatus = doc.status;
  doc._originalPaymentStatus = doc.paymentStatus;
};
bookingSchema.post('init', rememberOriginalStates);

/**
 * Valida fechas antes de guardar
 */
bookingSchema.pre('save', function(next) {
  if (this.checkIn) this.checkIn = normalizeDate(this.checkIn);
  if (this.checkOut) this.checkOut = normalizeDate(this.checkOut);

  if (this.checkIn && this.checkOut && this.checkOut <= this.checkIn) {
    return next(new Error('La fecha de check-out debe ser posterior al check-in'));
  }

  // Solo se exige "no pasado" al crear o al mover las fechas
  if (this.checkIn && (this.isNew || this.isModified('checkIn')) && this.checkIn < todayCalendarDate()) {
    return next(new Error('La fecha de check-in no puede ser anterior a hoy'));
  }

  next();
});

/**
 * Actualizar subtotal antes de guardar (si no se calculó)
 */
bookingSchema.pre('save', function(next) {
  // Una reserva pendiente nueva nace con retención; al salir de "pendiente"
  // (confirmada, cancelada...) ya no hay nada que vencer. "expired" conserva
  // la hora como registro.
  if (this.isNew && this.status === BOOKING_STATUS.PENDING && !this.holdExpiresAt) {
    this.holdExpiresAt = holdDeadline();
  }
  if (!this.isNew && this.isModified('status') &&
      ![BOOKING_STATUS.PENDING, BOOKING_STATUS.EXPIRED].includes(this.status)) {
    this.holdExpiresAt = undefined;
  }
  next();
});

bookingSchema.pre('save', async function(next) {
  if (this.isModified('subtotal') || this.isModified('experiencesTotal')) {
    this.totalPrice = (this.subtotal || 0) + (this.experiencesTotal || 0) - (this.discount || 0);
  }
  next();
});

/**
 * Validar transiciones de estado
 */
bookingSchema.pre('save', function(next) {
  if (!this.isNew && this.isModified('status') && this._originalStatus) {
    const allowed = STATUS_TRANSITIONS[this._originalStatus] || [];
    if (this._originalStatus !== this.status && !allowed.includes(this.status)) {
      return next(new Error(`Transición de estado inválida: ${this._originalStatus} -> ${this.status}`));
    }
  }

  if (!this.isNew && this.isModified('paymentStatus') && this._originalPaymentStatus) {
    const allowed = PAYMENT_STATUS_TRANSITIONS[this._originalPaymentStatus] || [];
    if (this._originalPaymentStatus !== this.paymentStatus && !allowed.includes(this.paymentStatus)) {
      return next(new Error(`Transición de estado de pago inválida: ${this._originalPaymentStatus} -> ${this.paymentStatus}`));
    }
  }

  next();
});

/**
 * Tras guardar: actualiza los estados "originales" y, si la reserva dejó de
 * bloquear noches (cancelada, completada, no_show), libera sus noches.
 * Si la liberación falla, las noches quedan BLOQUEADAS (fallo seguro: nunca
 * sobrevende); scripts/backfillSuiteNights.js reconcilia.
 */
bookingSchema.post('save', async function(doc) {
  rememberOriginalStates(doc);
  if (!BLOCKING_BOOKING_STATUSES.includes(doc.status)) {
    try {
      await SuiteNight.release(doc._id);
    } catch (err) {
      console.error(`[Booking] No se pudieron liberar las noches de ${doc._id}:`, err.message);
    }
  }
});

// ============================================
// VIRTUALS
// ============================================

/**
 * Número de noches de la estancia
 */
bookingSchema.virtual('nights').get(function() {
  if (this.checkIn && this.checkOut) {
    return calculateNights(this.checkIn, this.checkOut);
  }
  return 0;
});

/**
 * Precio por noche
 */
bookingSchema.virtual('pricePerNight').get(function() {
  const nights = this.nights;
  if (nights > 0 && this.subtotal) {
    return this.subtotal / nights;
  }
  return 0;
});

/**
 * Estado formateado para mostrar
 */
bookingSchema.virtual('statusLabel').get(function() {
  const labels = {
    [BOOKING_STATUS.PENDING]: 'Pendiente',
    [BOOKING_STATUS.CONFIRMED]: 'Confirmada',
    [BOOKING_STATUS.CHECKED_IN]: 'En el hotel (Check-in)',
    [BOOKING_STATUS.CHECKED_OUT]: 'Salida realizada (Check-out)',
    [BOOKING_STATUS.CANCELLED]: 'Cancelada',
    [BOOKING_STATUS.COMPLETED]: 'Completada',
    [BOOKING_STATUS.NO_SHOW]: 'No se presentó',
    [BOOKING_STATUS.EXPIRED]: 'Vencida'
  };
  return labels[this.status] || this.status;
});

/**
 * Estado de pago formateado
 */
bookingSchema.virtual('paymentStatusLabel').get(function() {
  const labels = {
    [PAYMENT_STATUS.PENDING]: 'Pendiente',
    [PAYMENT_STATUS.PAID]: 'Pagado',
    [PAYMENT_STATUS.FAILED]: 'Fallido',
    [PAYMENT_STATUS.REFUNDED]: 'Reembolsado',
    [PAYMENT_STATUS.PARTIAL]: 'Parcial'
  };
  return labels[this.paymentStatus] || this.paymentStatus;
});

/**
 * Verifica si la reserva es modificable
 */
bookingSchema.virtual('isModifiable').get(function() {
  const today = todayCalendarDate();
  if (this.status === BOOKING_STATUS.PENDING) return true;
  return this.status === BOOKING_STATUS.CONFIRMED && this.checkIn > today;
});

/**
 * Verifica si la reserva es cancelable
 */
bookingSchema.virtual('isCancellable').get(function() {
  const today = todayCalendarDate();
  if ([BOOKING_STATUS.CANCELLED, BOOKING_STATUS.COMPLETED, BOOKING_STATUS.NO_SHOW, BOOKING_STATUS.EXPIRED].includes(this.status)) {
    return false;
  }
  if (this.status === BOOKING_STATUS.PENDING) return true;
  return this.status === BOOKING_STATUS.CONFIRMED && this.checkIn > today;
});

// ============================================
// INSTANCE METHODS
// ============================================

// ============================================
// STATIC METHODS
// ============================================

/**
 * Verifica disponibilidad de suite para fechas
 */
bookingSchema.statics.checkSuiteAvailability = async function(suiteId, checkIn, checkOut, excludeBookingId = null) {
  const suite = await mongoose.model('Suite').findById(suiteId).select('totalUnits units available');
  if (!suite || !suite.available) return false;
  const capacity = suite.availableUnitsCount;
  if (capacity <= 0) return false;

  const checkInDate = normalizeDate(checkIn);
  const checkOutDate = normalizeDate(checkOut);
  if (!checkInDate || !checkOutDate || checkOutDate <= checkInDate) return false;

  const nights = eachNight(checkInDate, checkOutDate);
  if (!nights.length) return false;

  // Fuente de verdad: SuiteNight (bloqueos atómicos por habitación física).
  // Valida que exista al menos una habitación física libre para todo el rango.
  const free = await SuiteNight.freeSlots(suiteId, nights, capacity, excludeBookingId);
  return free.length > 0;
};

// ============================================
// ÍNDICES
// ============================================

// Índices existentes
// user, suite, status y paymentStatus ya declaran "index: true" en su
// propio campo; transactionId ya es "unique: true" (que también crea un
// índice). Repetirlos aquí producía el aviso "Duplicate schema index" de
// Mongoose para cada uno de ellos.
bookingSchema.index({ checkIn: 1, checkOut: 1 });
bookingSchema.index({ createdAt: -1 });
bookingSchema.index({ bookingDate: -1 });

// Índices adicionales para consultas comunes
bookingSchema.index({ guestEmail: 1 });
bookingSchema.index({ status: 1, checkIn: 1 });
bookingSchema.index({ status: 1, paymentStatus: 1, holdExpiresAt: 1 }); // barrendero de vencidas optimizado
bookingSchema.index({ user: 1, status: 1, suite: 1, checkIn: 1 }); // reserva repetida
bookingSchema.index({ user: 1, status: 1, checkOut: 1 });
bookingSchema.index({ 'cancellationDetails.cancelledBy': 1 });

// Índice compuesto para búsquedas por fechas y estado
bookingSchema.index({ checkIn: 1, status: 1 });
bookingSchema.index({ checkOut: 1, status: 1 });

// ============================================
// EXPORTAR MODELO Y CONSTANTES
// ============================================

const Booking = mongoose.model('Booking', bookingSchema);

export default Booking;
export {
  BOOKING_STATUS,
  PAYMENT_STATUS,
  BLOCKING_BOOKING_STATUSES,
  STATUS_TRANSITIONS,
  PAYMENT_STATUS_TRANSITIONS
};