import mongoose from 'mongoose';

// ============================================
// CONSTANTES
// ============================================

const EXPERIENCE_CATEGORIES = {
  AVENTURA: 'aventura',
  GASTRONOMIA: 'gastronomía',
  CULTURAL: 'cultural',
  RELAJACION: 'relajación',
  TOUR: 'tour',
  NATURALEZA: 'naturaleza',
  NOCHE: 'noche'
};

const CATEGORY_LABELS = {
  [EXPERIENCE_CATEGORIES.AVENTURA]: 'Aventura',
  [EXPERIENCE_CATEGORIES.GASTRONOMIA]: 'Gastronomía',
  [EXPERIENCE_CATEGORIES.CULTURAL]: 'Cultural',
  [EXPERIENCE_CATEGORIES.RELAJACION]: 'Relajación',
  [EXPERIENCE_CATEGORIES.TOUR]: 'Tour',
  [EXPERIENCE_CATEGORIES.NATURALEZA]: 'Naturaleza',
  [EXPERIENCE_CATEGORIES.NOCHE]: 'Noche'
};

const DIFFICULTY_LEVELS = {
  EASY: 'easy',
  MODERATE: 'moderate',
  HARD: 'hard'
};

// ============================================
// SUBSCHEMAS
// ============================================

/**
 * Horario de la experiencia
 */
const ScheduleSchema = new mongoose.Schema({
  dayOfWeek: {
    type: Number,
    min: 0,
    max: 6,
    required: true
  },
  startTime: {
    type: String,
    match: /^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/,
    required: true
  },
  endTime: {
    type: String,
    match: /^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$/,
    required: true
  },
  maxParticipants: {
    type: Number,
    min: 1
  },
  language: {
    type: String,
    default: 'es'
  }
});

/**
 * Disponibilidad por fecha específica
 */
const DateAvailabilitySchema = new mongoose.Schema({
  date: {
    type: Date,
    required: true
  },
  available: {
    type: Boolean,
    default: true
  },
  availableSpots: {
    type: Number,
    min: 0
  },
  specialPrice: {
    type: Number,
    min: 0
  },
  notes: String
});

/**
 * Review de la experiencia
 */
const ReviewSchema = new mongoose.Schema({
  user: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  rating: {
    type: Number,
    required: true,
    min: 1,
    max: 5
  },
  comment: {
    type: String,
    maxlength: 500
  },
  date: {
    type: Date,
    default: Date.now
  }
});

// ============================================
// MAIN SCHEMA
// ============================================

const experienceSchema = new mongoose.Schema({
  // Información básica
  name: { 
    type: String, 
    required: [true, 'El nombre es obligatorio'],
    trim: true,
    maxlength: [100, 'El nombre no puede exceder 100 caracteres'],
    unique: true,
    index: true
  },
  
  slug: {
    type: String,
    required: true,
    unique: true,
    lowercase: true,
    trim: true
  },
  
  shortDescription: { 
    type: String, 
    maxlength: [200, 'La descripción corta no puede exceder 200 caracteres'],
    required: [true, 'La descripción corta es obligatoria']
  },
  
  description: { 
    type: String, 
    required: [true, 'La descripción es obligatoria'],
    maxlength: [5000, 'La descripción no puede exceder 5000 caracteres']
  },
  
  // Categorización
  category: {
    type: String,
    enum: Object.values(EXPERIENCE_CATEGORIES),
    default: EXPERIENCE_CATEGORIES.TOUR,
    required: true,
    index: true
  },
  
  subcategory: {
    type: String,
    trim: true
  },
  
  difficulty: {
    type: String,
    enum: Object.values(DIFFICULTY_LEVELS),
    default: DIFFICULTY_LEVELS.EASY
  },
  
  // Precios
  price: { 
    type: Number, 
    required: [true, 'El precio es obligatorio'],
    min: [0, 'El precio no puede ser negativo'],
    index: true
  },
  
  discountedPrice: {
    type: Number,
    min: 0,
    validate: {
      validator: function(v) {
        return !v || v < this.price;
      },
      message: 'El precio con descuento debe ser menor al precio original'
    }
  },
  
  discountEndDate: {
    type: Date
  },
  
  // Duración
  durationHours: { 
    type: Number, 
    required: [true, 'La duración es obligatoria'],
    min: [0.5, 'La duración mínima es 0.5 horas'],
    max: [72, 'La duración máxima es 72 horas']
  },
  
  durationText: { 
    type: String,
    default: function() {
      if (this.durationHours >= 24) {
        const days = Math.floor(this.durationHours / 24);
        const hours = this.durationHours % 24;
        return hours > 0 ? `${days} día${days > 1 ? 's' : ''} y ${hours} horas` : `${days} día${days > 1 ? 's' : ''}`;
      }
      return `${this.durationHours} hora${this.durationHours !== 1 ? 's' : ''}`;
    }
  },
  
  // Capacidad
  maxCapacity: { 
    type: Number, 
    default: 20,
    min: [1, 'La capacidad mínima es 1 persona'],
    max: [100, 'La capacidad máxima es 100 personas']
  },
  
  minParticipants: {
    type: Number,
    default: 1,
    min: 1
  },
  
  // Imágenes
  // ✅ CORREGIDO: validator flexible que acepta URLs de Unsplash y cualquier CDN
  mainImage: { 
    type: String,
    validate: {
      validator: function(v) {
        return !v || /^https?:\/\/.+/i.test(v);
      },
      message: 'URL de imagen inválida'
    }
  },
  
  images: [{ 
    type: String,
    validate: {
      validator: function(v) {
        return /^https?:\/\/.+/i.test(v);
      },
      message: 'URL de imagen inválida'
    }
  }],
  
  videoUrl: {
    type: String,
    validate: {
      validator: function(v) {
        return !v || /^(https?:\/\/.*(youtube\.com|youtu\.be|vimeo\.com).*)$/i.test(v);
      },
      message: 'URL de video inválida (solo YouTube o Vimeo)'
    }
  },
  
  // Incluye / No incluye
  included: [{ 
    type: String,
    trim: true
  }],
  
  notIncluded: [{ 
    type: String,
    trim: true
  }],
  
  requirements: [{ 
    type: String,
    trim: true
  }],
  
  recommendations: [{ 
    type: String,
    trim: true
  }],
  
  // Ubicación y horarios
  // ✅ CORREGIDO: location.name sigue siendo required, pero ahora el seed lo provee
  location: {
    name: { type: String, required: true },
    address: { type: String },
    meetingPoint: { type: String },
    coordinates: {
      lat: { type: Number },
      lng: { type: Number }
    }
  },
  
  schedules: [ScheduleSchema],
  
  dateAvailability: [DateAvailabilitySchema],
  
  // Guías/Instructores
  guides: [{
    name: String,
    bio: String,
    image: String,
    languages: [String]
  }],
  
  // Idiomas disponibles
  languages: [{
    type: String,
    default: ['es', 'en']
  }],
  
  // Estilo visual
  icon: { 
    type: String, 
    default: '✨'
  },
  
  color: {
    type: String,
    match: /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/,
    default: '#C9A96E'
  },
  
  // Reviews
  reviews: [ReviewSchema],
  
  averageRating: {
    type: Number,
    default: 0,
    min: 0,
    max: 5
  },
  
  reviewCount: {
    type: Number,
    default: 0
  },
  
  // Metadatos
  order: { 
    type: Number, 
    default: 0,
    index: true
  },
  
  featured: { 
    type: Boolean, 
    default: false,
    index: true
  },
  
  popular: {
    type: Boolean,
    default: false
  },
  
  new: {
    type: Boolean,
    default: false
  },
  
  available: { 
    type: Boolean, 
    default: true,
    index: true
  },
  
  tags: [{
    type: String,
    trim: true,
    lowercase: true
  }],
  
  // SEO
  metaTitle: {
    type: String,
    maxlength: 60
  },
  
  metaDescription: {
    type: String,
    maxlength: 160
  },
  
  // Auditoría
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  },
  
  updatedBy: {
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
 * Genera slug automáticamente antes de guardar
 */
// Igual que en Suite.js: el slug se genera en pre('validate'), no en
// pre('save'), porque el campo es "required" y la validación corre antes
// que los hooks de 'save' — generarlo ahí llegaba tarde y hacía fallar
// siempre la creación de experiencias nuevas.
experienceSchema.pre('validate', function(next) {
  if (this.isModified('name') || !this.slug) {
    this.slug = this.name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  // Calcular duración formateada
  if (this.isModified('durationHours') || !this.durationText) {
    this.durationText = this.durationHours >= 24
      ? `${Math.floor(this.durationHours / 24)} día${Math.floor(this.durationHours / 24) > 1 ? 's' : ''}${this.durationHours % 24 > 0 ? ` y ${this.durationHours % 24} horas` : ''}`
      : `${this.durationHours} hora${this.durationHours !== 1 ? 's' : ''}`;
  }

  next();
});

experienceSchema.pre('save', function(next) {
  this.updatedAt = new Date();
  next();
});

/**
 * Actualiza averageRating y reviewCount después de agregar una review
 */
experienceSchema.pre('save', function(next) {
  if (this.isModified('reviews')) {
    const reviews = this.reviews;
    const total = reviews.reduce((sum, review) => sum + review.rating, 0);
    this.averageRating = reviews.length > 0 ? total / reviews.length : 0;
    this.reviewCount = reviews.length;
  }
  next();
});

/**
 * Valida que discountedPrice tenga fecha de expiración
 */
experienceSchema.pre('save', function(next) {
  if (this.discountedPrice && !this.discountEndDate) {
    next(new Error('Si hay precio con descuento, debe especificar una fecha de expiración'));
  }
  next();
});

// ============================================
// VIRTUALS
// ============================================

/**
 * Precio formateado en COP
 */
experienceSchema.virtual('formattedPrice').get(function() {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 0
  }).format(this.price);
});

/**
 * Precio con descuento formateado
 */
experienceSchema.virtual('formattedDiscountedPrice').get(function() {
  if (this.discountedPrice) {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      minimumFractionDigits: 0
    }).format(this.discountedPrice);
  }
  return null;
});

/**
 * Verifica si el descuento está vigente
 */
experienceSchema.virtual('isDiscounted').get(function() {
  return this.discountedPrice && 
         this.discountEndDate && 
         new Date() <= this.discountEndDate;
});

/**
 * Porcentaje de descuento
 */
experienceSchema.virtual('discountPercentage').get(function() {
  if (this.isDiscounted && this.price > 0) {
    return Math.round(((this.price - this.discountedPrice) / this.price) * 100);
  }
  return 0;
});

/**
 * Etiqueta de categoría en español
 */
experienceSchema.virtual('categoryLabel').get(function() {
  return CATEGORY_LABELS[this.category] || this.category;
});

/**
 * Verifica disponibilidad para una fecha específica
 */
experienceSchema.virtual('isAvailableForDate').get(function() {
  return function(date) {
    if (!this.available) return false;
    
    const availability = this.dateAvailability.find(
      a => a.date.toDateString() === date.toDateString()
    );
    
    if (availability) {
      return availability.available && (!availability.availableSpots || availability.availableSpots > 0);
    }
    
    // Verificar horario por día de semana
    const dayOfWeek = date.getDay();
    const schedule = this.schedules.find(s => s.dayOfWeek === dayOfWeek);
    
    return !!schedule && (!schedule.maxParticipants || schedule.maxParticipants > 0);
  };
});

// ============================================
// INSTANCE METHODS
// ============================================

/**
 * Verifica disponibilidad para una fecha específica
 */
experienceSchema.methods.checkAvailability = async function(date, participants = 1) {
  if (!this.available) return false;
  if (participants > this.maxCapacity) return false;
  
  const availability = this.dateAvailability.find(
    a => a.date.toDateString() === date.toDateString()
  );
  
  if (availability) {
    return availability.available && 
           (!availability.availableSpots || availability.availableSpots >= participants);
  }
  
  const dayOfWeek = date.getDay();
  const schedule = this.schedules.find(s => s.dayOfWeek === dayOfWeek);
  
  if (!schedule) return false;
  
  const maxParticipants = schedule.maxParticipants || this.maxCapacity;
  return participants <= maxParticipants;
};

/**
 * Agrega una review
 */
experienceSchema.methods.addReview = async function(userId, rating, comment) {
  this.reviews.push({
    user: userId,
    rating,
    comment,
    date: new Date()
  });
  
  await this.save();
  return this;
};

/**
 * Obtiene próximas fechas disponibles
 */
experienceSchema.methods.getUpcomingAvailability = async function(days = 30) {
  const dates = [];
  const today = new Date();
  
  for (let i = 0; i < days; i++) {
    const date = new Date(today);
    date.setDate(today.getDate() + i);
    
    const isAvailable = await this.checkAvailability(date);
    if (isAvailable) {
      dates.push(date);
    }
  }
  
  return dates;
};

// ============================================
// STATIC METHODS
// ============================================

/**
 * Obtiene experiencias destacadas
 */
experienceSchema.statics.getFeatured = function(limit = 4) {
  return this.find({ featured: true, available: true })
    .sort({ order: 1, createdAt: -1 })
    .limit(limit);
};

/**
 * Obtiene experiencias por categoría
 */
experienceSchema.statics.getByCategory = function(category, limit = 10) {
  return this.find({ category, available: true })
    .sort({ order: 1, price: 1 })
    .limit(limit);
};

/**
 * Búsqueda de experiencias con filtros
 */
experienceSchema.statics.search = function(filters = {}) {
  const query = { available: true };
  
  if (filters.category) {
    query.category = filters.category;
  }
  
  if (filters.minPrice) {
    query.price = { ...query.price, $gte: filters.minPrice };
  }
  
  if (filters.maxPrice) {
    query.price = { ...query.price, $lte: filters.maxPrice };
  }
  
  if (filters.difficulty) {
    query.difficulty = filters.difficulty;
  }
  
  if (filters.search) {
    query.$text = { $search: filters.search };
  }
  
  if (filters.featured) {
    query.featured = true;
  }
  
  if (filters.popular) {
    query.popular = true;
  }
  
  let sort = {};
  if (filters.sortBy === 'price_asc') sort = { price: 1 };
  else if (filters.sortBy === 'price_desc') sort = { price: -1 };
  else if (filters.sortBy === 'rating') sort = { averageRating: -1 };
  else if (filters.sortBy === 'newest') sort = { createdAt: -1 };
  else sort = { order: 1, createdAt: -1 };
  
  const limit = filters.limit || 20;
  const skip = filters.page ? (filters.page - 1) * limit : 0;
  
  return this.find(query)
    .sort(sort)
    .skip(skip)
    .limit(limit);
};

/**
 * Obtiene estadísticas de experiencias
 */
experienceSchema.statics.getStats = async function() {
  const stats = await this.aggregate([
    { $match: { available: true } },
    {
      $group: {
        _id: null,
        total: { $sum: 1 },
        avgPrice: { $avg: '$price' },
        minPrice: { $min: '$price' },
        maxPrice: { $max: '$price' },
        avgRating: { $avg: '$averageRating' }
      }
    }
  ]);
  
  const byCategory = await this.aggregate([
    { $match: { available: true } },
    {
      $group: {
        _id: '$category',
        count: { $sum: 1 },
        avgPrice: { $avg: '$price' }
      }
    },
    { $sort: { count: -1 } }
  ]);
  
  return {
    general: stats[0] || { total: 0, avgPrice: 0, minPrice: 0, maxPrice: 0, avgRating: 0 },
    byCategory
  };
};

/**
 * Obtiene todas las categorías disponibles
 */
experienceSchema.statics.getCategories = function() {
  return this.distinct('category', { available: true });
};

// ============================================
// ÍNDICES
// ============================================

// Índices de texto para búsqueda
experienceSchema.index({ name: 'text', description: 'text' });

// Índices simples. slug ya es "unique: true" en el campo (crea su propio
// índice) y createdAt ya tiene "index: true" ahí mismo: declararlos otra
// vez aquí generaba el aviso "Duplicate schema index" de Mongoose.
experienceSchema.index({ averageRating: -1 });
experienceSchema.index({ popular: 1 });
experienceSchema.index({ 'location.coordinates': '2dsphere' });
experienceSchema.index({ tags: 1 });

// Índices compuestos para búsquedas comunes
experienceSchema.index({ available: 1, featured: 1, order: 1 });
experienceSchema.index({ category: 1, price: 1 });

// ============================================
// EXPORTAR MODELO Y CONSTANTES
// ============================================

const Experience = mongoose.model('Experience', experienceSchema);

export default Experience;
export { EXPERIENCE_CATEGORIES, CATEGORY_LABELS, DIFFICULTY_LEVELS };