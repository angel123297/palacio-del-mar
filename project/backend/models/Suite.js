import mongoose from 'mongoose';

// ============================================
// CONSTANTES
// ============================================

const SUITE_TYPES = {
  ROOM: 'Habitación',
  DELUXE: 'Suite Deluxe',
  PREMIUM: 'Suite Premium',
  PRESIDENTIAL: 'Suite Presidencial',
  EXCLUSIVE: 'Suite Exclusiva',
  PENTHOUSE: 'Penthouse'
};

const SUITE_TYPE_LABELS = {
  [SUITE_TYPES.ROOM]: 'Habitación Estándar',
  [SUITE_TYPES.DELUXE]: 'Suite Deluxe',
  [SUITE_TYPES.PREMIUM]: 'Suite Premium',
  [SUITE_TYPES.PRESIDENTIAL]: 'Suite Presidencial',
  [SUITE_TYPES.EXCLUSIVE]: 'Suite Exclusiva',
  [SUITE_TYPES.PENTHOUSE]: 'Penthouse'
};

const BED_TYPES = {
  SINGLE: 'single',
  DOUBLE: 'double',
  QUEEN: 'queen',
  KING: 'king',
  SUPER_KING: 'super_king'
};

const BED_TYPE_LABELS = {
  [BED_TYPES.SINGLE]: 'Individual',
  [BED_TYPES.DOUBLE]: 'Doble',
  [BED_TYPES.QUEEN]: 'Queen',
  [BED_TYPES.KING]: 'King',
  [BED_TYPES.SUPER_KING]: 'Super King'
};

const VIEW_TYPES = {
  CITY: 'city',
  GARDEN: 'garden',
  POOL: 'pool',
  OCEAN: 'ocean',
  PARTIAL_OCEAN: 'partial_ocean',
  LANDMARK: 'landmark',
  COURTYARD: 'courtyard'
};

// ============================================
// SUBSCHEMAS
// ============================================

const BedConfigSchema = new mongoose.Schema({
  type: { type: String, enum: Object.values(BED_TYPES), required: true },
  quantity: { type: Number, default: 1, min: 1, max: 3 },
  size: { type: String, enum: ['single', 'double', 'queen', 'king'] }
});

const SeasonalPriceSchema = new mongoose.Schema({
  season: { type: String, enum: ['low', 'mid', 'high', 'peak'], required: true },
  multiplier: { type: Number, default: 1.0, min: 0.5, max: 2.0 },
  startDate: { type: Date, required: true },
  endDate: { type: Date, required: true },
  isActive: { type: Boolean, default: true }
});

const UnitAvailabilitySchema = new mongoose.Schema({
  unitNumber: { type: String, required: true },
  floor: { type: Number, min: 1 },
  isAvailable: { type: Boolean, default: true },
  maintenanceDates: [{ startDate: Date, endDate: Date, reason: String }],
  notes: String
});

const ReviewSchema = new mongoose.Schema({
  user: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  rating: { type: Number, required: true, min: 1, max: 5 },
  comment: { type: String, maxlength: 500 },
  date: { type: Date, default: Date.now }
});

// ============================================
// MAIN SCHEMA
// ============================================

const suiteSchema = new mongoose.Schema({
  name: { type: String, required: true, trim: true, unique: true, index: true },
  slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
  type: { type: String, required: true, enum: Object.values(SUITE_TYPES), index: true },
  description: { type: String, required: true },
  shortDescription: { type: String },
  
  basePrice: { type: Number, required: true, min: 0, index: true },
  originalPrice: { type: Number, min: 0 },
  seasonalPrices: [SeasonalPriceSchema],
  
  size: { type: Number, required: true },
  sizeUnit: { type: String, enum: ['m²', 'ft²'], default: 'm²' },
  floor: { type: Number, min: 1, max: 20 },
  
  maxGuests: { type: Number, default: 2, min: 1, max: 12, index: true },
  beds: [BedConfigSchema],
  extraBeds: { available: { type: Boolean, default: false }, price: { type: Number, default: 0 }, maxExtra: { type: Number, default: 1 } },
  bathrooms: { type: Number, default: 1, min: 1, max: 5 },
  
  hasJacuzzi: { type: Boolean, default: false },
  hasBalcony: { type: Boolean, default: false },
  hasTerrace: { type: Boolean, default: false },
  view: { type: String, enum: Object.values(VIEW_TYPES), default: VIEW_TYPES.CITY },
  
  // IMÁGENES - SIN VALIDACIÓN PARA EVITAR ERRORES
  mainImage: { type: String },
  images: [{ type: String }],
  floorPlan: { type: String },
  
  amenities: [{ type: String, trim: true }],
  features: [{ type: String, trim: true }],
  safetyFeatures: [{ type: String, trim: true }],
  technology: [{ type: String, trim: true }],
  
  totalUnits: { type: Number, default: 1, min: 1 },
  units: [UnitAvailabilitySchema],
  available: { type: Boolean, default: true, index: true },
  minimumStay: { type: Number, default: 1, min: 1, max: 30 },
  maximumStay: { type: Number, default: 90, min: 1, max: 365 },
  
  reviews: [ReviewSchema],
  averageRating: { type: Number, default: 0, min: 0, max: 5 },
  reviewCount: { type: Number, default: 0 },
  
  order: { type: Number, default: 0, index: true },
  featured: { type: Boolean, default: false },
  popular: { type: Boolean, default: false },
  tags: [{ type: String, trim: true, lowercase: true }],
  
  metaTitle: { type: String, maxlength: 60 },
  metaDescription: { type: String, maxlength: 160 },
  
  createdBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  updatedBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  
  createdAt: { type: Date, default: Date.now, index: true },
  updatedAt: { type: Date, default: Date.now }
}, {
  timestamps: true,
  toJSON: { virtuals: true, getters: true },
  toObject: { virtuals: true }
});

// ============================================
// MIDDLEWARES
// ============================================

// IMPORTANTE: esto va en pre('validate'), no en pre('save'). Mongoose
// valida el documento ANTES de correr los hooks de 'save', así que si el
// slug (campo "required") se generaba en pre('save') nunca llegaba a
// tiempo para la validación de un documento nuevo: crear una suite
// siempre fallaba con "slug: Path `slug` is required.".
suiteSchema.pre('validate', function(next) {
  if (this.isModified('name') || !this.slug) {
    this.slug = this.name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  if (!this.shortDescription && this.description) {
    this.shortDescription = this.description.substring(0, 200);
  }

  next();
});

suiteSchema.pre('save', function(next) {
  this.updatedAt = new Date();
  next();
});

suiteSchema.pre('save', function(next) {
  if (this.isModified('reviews')) {
    const reviews = this.reviews;
    const total = reviews.reduce((sum, review) => sum + review.rating, 0);
    this.averageRating = reviews.length > 0 ? total / reviews.length : 0;
    this.reviewCount = reviews.length;
  }
  next();
});

// ============================================
// VIRTUALS
// ============================================

suiteSchema.virtual('formattedBasePrice').get(function() {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 0
  }).format(this.basePrice);
});

suiteSchema.virtual('formattedOriginalPrice').get(function() {
  if (this.originalPrice) {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      minimumFractionDigits: 0
    }).format(this.originalPrice);
  }
  return null;
});

suiteSchema.virtual('hasDiscount').get(function() {
  return this.originalPrice && this.originalPrice > this.basePrice;
});

suiteSchema.virtual('discountPercentage').get(function() {
  if (this.hasDiscount && this.basePrice > 0) {
    return Math.round(((this.originalPrice - this.basePrice) / this.originalPrice) * 100);
  }
  return 0;
});

suiteSchema.virtual('typeLabel').get(function() {
  return SUITE_TYPE_LABELS[this.type] || this.type;
});

suiteSchema.virtual('viewLabel').get(function() {
  const labels = {
    [VIEW_TYPES.CITY]: 'Vista a la ciudad',
    [VIEW_TYPES.GARDEN]: 'Vista al jardín',
    [VIEW_TYPES.POOL]: 'Vista a la piscina',
    [VIEW_TYPES.OCEAN]: 'Vista al mar',
    [VIEW_TYPES.PARTIAL_OCEAN]: 'Vista parcial al mar',
    [VIEW_TYPES.LANDMARK]: 'Vista a monumento',
    [VIEW_TYPES.COURTYARD]: 'Vista al patio'
  };
  return labels[this.view] || this.view;
});

suiteSchema.virtual('bedDescription').get(function() {
  return this.beds.map(bed => {
    const typeLabel = BED_TYPE_LABELS[bed.type] || bed.type;
    const quantity = bed.quantity > 1 ? `${bed.quantity} ` : '';
    return `${quantity}${typeLabel}`;
  }).join(' + ');
});

suiteSchema.virtual('hasAvailableUnits').get(function() {
  if (!this.available) return false;
  if (this.units && this.units.length > 0) {
    return this.units.some(unit => unit.isAvailable);
  }
  return true;
});

suiteSchema.virtual('availableUnitsCount').get(function() {
  if (this.units && this.units.length > 0) {
    return this.units.filter(unit => unit.isAvailable).length;
  }
  return this.available ? this.totalUnits : 0;
});

// ============================================
// INSTANCE METHODS
// ============================================

suiteSchema.methods.calculatePrice = function(checkIn, checkOut, includeFees = true) {
  const nights = Math.ceil((checkOut - checkIn) / (1000 * 60 * 60 * 24));
  let multiplier = 1.0;
  
  for (const seasonalPrice of this.seasonalPrices) {
    if (seasonalPrice.isActive && checkIn >= seasonalPrice.startDate && checkIn <= seasonalPrice.endDate) {
      multiplier = seasonalPrice.multiplier;
      break;
    }
  }
  
  const nightlyPrice = this.basePrice * multiplier;
  let subtotal = nightlyPrice * nights;
  let fees = includeFees ? subtotal * 0.19 : 0;
  
  return { nightlyPrice, nights, subtotal, fees, total: subtotal + fees, multiplier, pricePerNight: nightlyPrice };
};

suiteSchema.methods.checkAvailability = async function(checkIn, checkOut, quantity = 1) {
  const Booking = mongoose.model('Booking');
  
  const conflictingBookings = await Booking.countDocuments({
    suite: this._id,
    status: { $in: ['pending', 'confirmed', 'paid'] },
    checkIn: { $lt: checkOut },
    checkOut: { $gt: checkIn }
  });
  
  const availableUnits = this.availableUnitsCount;
  const remainingUnits = availableUnits - conflictingBookings;
  
  return { available: remainingUnits >= quantity, availableUnits: remainingUnits, requestedUnits: quantity, totalUnits: this.totalUnits, conflictingBookings };
};

suiteSchema.methods.addReview = async function(userId, rating, comment) {
  this.reviews.push({ user: userId, rating, comment, date: new Date() });
  await this.save();
  return this;
};

// ============================================
// STATIC METHODS
// ============================================

suiteSchema.statics.getFeatured = function(limit = 4) {
  return this.find({ featured: true, available: true }).sort({ order: 1, basePrice: 1 }).limit(limit);
};

// ============================================
// ÍNDICES
// ============================================

// type, basePrice, maxGuests, order, available y slug ya declaran su
// propio "index: true" / "unique: true" en la definición del campo;
// repetirlos aquí producía el aviso "Duplicate schema index" de Mongoose.
suiteSchema.index({ name: 'text', type: 'text', description: 'text' });
suiteSchema.index({ featured: 1 });
suiteSchema.index({ available: 1, featured: 1, order: 1 });

// ============================================
// EXPORTAR
// ============================================

const Suite = mongoose.model('Suite', suiteSchema);

export default Suite;
export { SUITE_TYPES, SUITE_TYPE_LABELS, BED_TYPES, BED_TYPE_LABELS, VIEW_TYPES };