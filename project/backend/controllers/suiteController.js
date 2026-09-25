import Suite from '../models/Suite.js';

// ============================================
// CONFIGURACIÓN
// ============================================

const DEFAULT_PAGE = 1;
const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 50;
const CACHE_TTL = 300; // 5 minutos en caché

// Caché simple en memoria
let cache = new Map();

// Mapeo de temporadas y sus multiplicadores de precio
const SEASON_MULTIPLIERS = {
  low: 1.0,      // Temporada baja
  mid: 1.15,     // Temporada media
  high: 1.3,     // Temporada alta
  peak: 1.5      // Temporada pico (Navidad, Semana Santa)
};

// Fechas de temporadas (formato: MM-DD)
const SEASON_DATES = {
  high: [
    { start: '06-15', end: '07-15' },  // Vacaciones de mitad de año
    { start: '12-15', end: '12-20' }   // Pre-navidad
  ],
  peak: [
    { start: '12-21', end: '01-10' },  // Navidad y Año Nuevo
    { start: '03-24', end: '04-08' }   // Semana Santa (aproximado)
  ]
};

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Limpia caracteres peligrosos
 */
const sanitizeString = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str.replace(/[<>{}[\]$?;]/g, '');
};

/**
 * Escapa metacaracteres de regex antes de usar un string en un $regex de
 * MongoDB (SEC-020). sanitizeString ya quita < > { } [ ] $ ? ; pero deja
 * intactos ( ) + * . | ^ — un valor como "(a+)+b" (parcialmente filtrado,
 * pero "(a+)+" sobrevive) puede provocar backtracking catastrófico en el
 * motor de regex durante la búsqueda, colgando esa consulta (ReDoS).
 */
const escapeRegex = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};

/**
 * Valida que el ID de MongoDB sea válido
 */
const isValidObjectId = (id) => {
  return /^[0-9a-fA-F]{24}$/.test(id);
};

/**
 * Determina la temporada basada en una fecha
 */
export const getSeason = (date) => {
  const monthDay = date.toISOString().slice(5, 10); // Formato "MM-DD"
  
  // Verificar temporada pico
  for (const season of SEASON_DATES.peak) {
    if (monthDay >= season.start && monthDay <= season.end) {
      return 'peak';
    }
  }
  
  // Verificar temporada alta
  for (const season of SEASON_DATES.high) {
    if (monthDay >= season.start && monthDay <= season.end) {
      return 'high';
    }
  }
  
  // Verificar temporada media (Junio y Diciembre fuera de fechas pico)
  const month = parseInt(monthDay.slice(0, 2));
  if ((month === 6 && monthDay > '07-15') || (month === 12 && monthDay < '12-15')) {
    return 'mid';
  }
  
  return 'low';
};

/**
 * Calcula el precio según temporada
 */
export const calculateSeasonalPrice = (basePrice, season) => {
  const multiplier = SEASON_MULTIPLIERS[season] || 1.0;
  return Math.round(basePrice * multiplier);
};

/**
 * Valida y sanitiza los parámetros de consulta
 */
const validateQueryParams = (query) => {
  const {
    page,
    limit,
    sortBy,
    sortOrder,
    minPrice,
    maxPrice,
    minSize,
    maxSize,
    minGuests,
    maxGuests,
    amenities,
    type,
    available,
    search,
    checkIn,
    checkOut
  } = query;
  
  const validated = {};
  
  // Paginación
  validated.page = Math.max(1, parseInt(page) || DEFAULT_PAGE);
  validated.limit = Math.min(MAX_LIMIT, Math.max(1, parseInt(limit) || DEFAULT_LIMIT));
  
  // Ordenamiento
  const validSortFields = ['price', 'size', 'name', 'order', 'maxGuests', 'createdAt'];
  validated.sortBy = validSortFields.includes(sortBy) ? sortBy : 'order';
  validated.sortOrder = sortOrder === 'desc' ? -1 : 1;
  
  // Filtros de precio
  validated.minPrice = minPrice ? Math.max(0, parseInt(minPrice)) : null;
  validated.maxPrice = maxPrice ? Math.max(0, parseInt(maxPrice)) : null;
  
  if (validated.minPrice !== null && validated.maxPrice !== null && validated.minPrice > validated.maxPrice) {
    throw new Error('El precio mínimo no puede ser mayor al precio máximo');
  }
  
  // Filtros de tamaño (m²)
  validated.minSize = minSize ? Math.max(0, parseInt(minSize)) : null;
  validated.maxSize = maxSize ? Math.max(0, parseInt(maxSize)) : null;
  
  // Filtros de huéspedes
  validated.minGuests = minGuests ? Math.max(1, parseInt(minGuests)) : null;
  validated.maxGuests = maxGuests ? Math.max(1, parseInt(maxGuests)) : null;
  
  // Amenities (puede ser string o array)
  if (amenities) {
    validated.amenities = Array.isArray(amenities) ? amenities : amenities.split(',');
  }
  
  // Tipo de suite
  const validTypes = ['Habitación', 'Suite Deluxe', 'Suite Premium', 'Suite Presidencial', 'Suite Exclusiva'];
  validated.type = validTypes.includes(type) ? type : null;
  
  // Disponibilidad
  validated.available = available === 'false' ? false : true;
  
  // Búsqueda
  validated.search = search ? sanitizeString(search).trim() : null;
  
  // Fechas (para disponibilidad)
  validated.checkIn = checkIn ? new Date(checkIn) : null;
  validated.checkOut = checkOut ? new Date(checkOut) : null;
  
  if (validated.checkIn && validated.checkOut && validated.checkIn >= validated.checkOut) {
    throw new Error('La fecha de check-out debe ser posterior al check-in');
  }
  
  return validated;
};

/**
 * Construye el query de MongoDB basado en los filtros
 */
const buildQuery = (filters) => {
  const query = {};
  
  // Filtro de disponibilidad general
  if (filters.available !== undefined) {
    query.available = filters.available;
  }
  
  // Filtro de precio (precio base, antes de temporada)
  if (filters.minPrice !== null || filters.maxPrice !== null) {
    query.price = {};
    if (filters.minPrice !== null) query.price.$gte = filters.minPrice;
    if (filters.maxPrice !== null) query.price.$lte = filters.maxPrice;
  }
  
  // Filtro de tamaño
  if (filters.minSize !== null || filters.maxSize !== null) {
    query.size = {};
    if (filters.minSize !== null) query.size.$gte = filters.minSize;
    if (filters.maxSize !== null) query.size.$lte = filters.maxSize;
  }
  
  // Filtro de huéspedes
  if (filters.minGuests !== null || filters.maxGuests !== null) {
    query.maxGuests = {};
    if (filters.minGuests !== null) query.maxGuests.$gte = filters.minGuests;
    if (filters.maxGuests !== null) query.maxGuests.$lte = filters.maxGuests;
  }
  
  // Filtro de amenities (todos los especificados deben estar presentes)
  if (filters.amenities && filters.amenities.length > 0) {
    query.amenities = { $all: filters.amenities };
  }
  
  // Filtro de tipo
  if (filters.type) {
    query.type = filters.type;
  }
  
  // Búsqueda por texto
  if (filters.search) {
    const safeSearch = escapeRegex(filters.search);
    query.$or = [
      { name: { $regex: safeSearch, $options: 'i' } },
      { type: { $regex: safeSearch, $options: 'i' } },
      { description: { $regex: safeSearch, $options: 'i' } }
    ];
  }
  
  return query;
};

/**
 * Genera una clave de caché única
 */
const getCacheKey = (params) => {
  return `suites:${JSON.stringify(params)}`;
};

/**
 * Invalida la caché de suites
 */
const invalidateCache = () => {
  cache.clear();
  console.log('[Cache] Caché de suites invalidada');
};

// ============================================
// MAIN CONTROLLERS
// ============================================

/**
 * @desc    Obtener todas las suites con filtros y paginación
 * @route   GET /api/suites
 * @access  Public
 */
export const getSuites = async (req, res) => {
  const requestId = Math.random().toString(36).substring(7);
  const startTime = Date.now();
  
  try {
    // 1. Validar parámetros
    const filters = validateQueryParams(req.query);
    const { page, limit, sortBy, sortOrder, checkIn, checkOut } = filters;
    const skip = (page - 1) * limit;
    
    // 2. Construir query base
    const query = buildQuery(filters);
    
    // 3. Verificar caché
    const cacheKey = getCacheKey({ ...filters, page, limit });
    if (process.env.ENABLE_CACHE === 'true') {
      const cached = cache.get(cacheKey);
      if (cached && (Date.now() - cached.timestamp) < CACHE_TTL * 1000) {
        console.log(`[Cache] HIT ${requestId}`);
        return res.json(cached.data);
      }
    }
    
    // 4. Determinar temporada para precios dinámicos
    const referenceDate = checkIn || new Date();
    const season = getSeason(referenceDate);
    const priceMultiplier = SEASON_MULTIPLIERS[season];
    
    // 5. Ejecutar consultas en paralelo
    const [suites, total] = await Promise.all([
      Suite.find(query)
        .sort({ [sortBy]: sortOrder })
        .skip(skip)
        .limit(limit)
        .lean(),
      Suite.countDocuments(query)
    ]);
    
    // 6. Enriquecer suites con precios por temporada y disponibilidad
    const enrichedSuites = suites.map(suite => {
      const seasonalPrice = calculateSeasonalPrice(suite.basePrice, season);
      const seasonalOriginalPrice = suite.originalPrice 
        ? calculateSeasonalPrice(suite.originalPrice, season)
        : null;
      
      return {
        ...suite,
        seasonalPrice,
        seasonalOriginalPrice,
        season,
        priceMultiplier,
        priceNote: season === 'peak' ? 'Temporada alta' : (season === 'high' ? 'Temporada alta' : 'Precio regular')
      };
    });
    
    // 7. Calcular estadísticas de filtros disponibles
    const stats = await getSuiteStats(query);
    
    // 8. Obtener amenities disponibles (para filtros del frontend)
    const availableAmenities = await getAvailableAmenities();
    
    // 9. Preparar respuesta
    const response = {
      success: true,
      data: enrichedSuites,
      metadata: {
        pagination: {
          currentPage: page,
          totalPages: Math.ceil(total / limit),
          totalItems: total,
          itemsPerPage: limit,
          hasNextPage: page < Math.ceil(total / limit),
          hasPrevPage: page > 1
        },
        filters: {
          sortBy,
          sortOrder: sortOrder === 1 ? 'asc' : 'desc',
          minPrice: filters.minPrice,
          maxPrice: filters.maxPrice,
          minSize: filters.minSize,
          maxSize: filters.maxSize,
          minGuests: filters.minGuests,
          maxGuests: filters.maxGuests,
          type: filters.type,
          search: filters.search
        },
        season: {
          current: season,
          multiplier: priceMultiplier,
          note: season === 'peak' ? 'Precios de temporada alta aplicados' : 
                (season === 'high' ? 'Precios de temporada alta aplicados' : 'Precios regulares')
        },
        stats,
        availableAmenities,
        responseTime: Date.now() - startTime,
        requestId
      }
    };
    
    // 10. Guardar en caché
    if (process.env.ENABLE_CACHE === 'true') {
      cache.set(cacheKey, {
        data: response,
        timestamp: Date.now()
      });
      
      // Limpiar caché vieja
      if (cache.size > 100) {
        const oldestKey = cache.keys().next().value;
        cache.delete(oldestKey);
      }
    }
    
    res.json(response);
    
  } catch (error) {
    console.error(`[Suites Error ${requestId}]:`, error);
    
    if (error.message.includes('precio mínimo') || error.message.includes('check-out')) {
      return res.status(400).json({
        success: false,
        message: error.message
      });
    }
    
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor al obtener las suites',
      requestId
    });
  }
};

/**
 * @desc    Obtener una suite por ID
 * @route   GET /api/suites/:id
 * @access  Public
 */
export const getSuiteById = async (req, res) => {
  try {
    const { id } = req.params;
    const { checkIn, checkOut } = req.query;
    
    // Validar ID
    if (!isValidObjectId(id)) {
      return res.status(400).json({
        success: false,
        message: 'ID de suite inválido'
      });
    }
    
    // Obtener suite
    const suite = await Suite.findById(id);
    
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    // Determinar temporada y precios
    const referenceDate = checkIn ? new Date(checkIn) : new Date();
    const season = getSeason(referenceDate);
    const seasonalPrice = calculateSeasonalPrice(suite.basePrice, season);
    const seasonalOriginalPrice = suite.originalPrice 
      ? calculateSeasonalPrice(suite.originalPrice, season)
      : null;
    
    // Calcular noches y precio total si hay fechas
    let nights = null;
    let totalPrice = null;
    if (checkIn && checkOut) {
      const checkInDate = new Date(checkIn);
      const checkOutDate = new Date(checkOut);
      nights = Math.ceil((checkOutDate - checkInDate) / (1000 * 60 * 60 * 24));
      totalPrice = seasonalPrice * nights;
    }
    
    // Obtener suites relacionadas (mismo tipo o rango de precio similar)
    const relatedSuites = await Suite.find({
      _id: { $ne: id },
      available: true,
      $or: [
        { type: suite.type },
        { price: { $gte: suite.basePrice * 0.7, $lte: suite.basePrice * 1.3 } }
      ]
    })
    .limit(3)
    .select('name type price size image maxGuests');
    
    res.json({
      success: true,
      data: {
        ...suite.toObject(),
        seasonalPrice,
        seasonalOriginalPrice,
        season,
        priceMultiplier: SEASON_MULTIPLIERS[season],
        nights,
        totalPrice,
        priceNote: season === 'peak' ? 'Temporada alta - precios especiales' : 
                  (season === 'high' ? 'Temporada alta' : 'Precio regular')
      },
      related: relatedSuites
    });
    
  } catch (error) {
    console.error('[GetSuiteById Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener estadísticas de suites
 * @route   GET /api/suites/stats
 * @access  Public
 */
export const getSuiteStats = async (query = {}) => {
  try {
    const [stats, priceRange, sizeRange, guestRange, typeDistribution] = await Promise.all([
      Suite.aggregate([
        { $match: query },
        {
          $group: {
            _id: null,
            total: { $sum: 1 },
            avgPrice: { $avg: '$price' },
            minPrice: { $min: '$price' },
            maxPrice: { $max: '$price' },
            avgSize: { $avg: '$size' },
            minSize: { $min: '$size' },
            maxSize: { $max: '$size' },
            avgGuests: { $avg: '$maxGuests' },
            minGuests: { $min: '$maxGuests' },
            maxGuests: { $max: '$maxGuests' }
          }
        }
      ]),
      Suite.aggregate([
        { $match: { available: true } },
        {
          $group: {
            _id: null,
            min: { $min: '$price' },
            max: { $max: '$price' }
          }
        }
      ]),
      Suite.aggregate([
        { $match: { available: true } },
        {
          $group: {
            _id: null,
            min: { $min: '$size' },
            max: { $max: '$size' }
          }
        }
      ]),
      Suite.aggregate([
        { $match: { available: true } },
        {
          $group: {
            _id: null,
            min: { $min: '$maxGuests' },
            max: { $max: '$maxGuests' }
          }
        }
      ]),
      Suite.aggregate([
        { $match: { available: true } },
        {
          $group: {
            _id: '$type',
            count: { $sum: 1 }
          }
        }
      ])
    ]);
    
    return {
      total: stats[0]?.total || 0,
      averagePrice: Math.round(stats[0]?.avgPrice || 0),
      minPrice: stats[0]?.minPrice || 0,
      maxPrice: stats[0]?.maxPrice || 0,
      averageSize: Math.round(stats[0]?.avgSize || 0),
      minSize: stats[0]?.minSize || 0,
      maxSize: stats[0]?.maxSize || 0,
      averageGuests: Math.round(stats[0]?.avgGuests || 0),
      minGuests: stats[0]?.minGuests || 0,
      maxGuests: stats[0]?.maxGuests || 0,
      globalPriceRange: {
        min: priceRange[0]?.min || 0,
        max: priceRange[0]?.max || 0
      },
      globalSizeRange: {
        min: sizeRange[0]?.min || 0,
        max: sizeRange[0]?.max || 0
      },
      globalGuestRange: {
        min: guestRange[0]?.min || 0,
        max: guestRange[0]?.max || 0
      },
      typeDistribution: typeDistribution.reduce((acc, curr) => {
        acc[curr._id] = curr.count;
        return acc;
      }, {})
    };
    
  } catch (error) {
    console.error('[GetSuiteStats Error]:', error);
    return {
      total: 0,
      averagePrice: 0,
      minPrice: 0,
      maxPrice: 0,
      averageSize: 0,
      minSize: 0,
      maxSize: 0,
      averageGuests: 0,
      minGuests: 0,
      maxGuests: 0,
      globalPriceRange: { min: 0, max: 0 },
      globalSizeRange: { min: 0, max: 0 },
      globalGuestRange: { min: 0, max: 0 },
      typeDistribution: {}
    };
  }
};

/**
 * @desc    Endpoint HTTP de estadísticas de suites.
 *
 * BUG encontrado al construir el dashboard de admin: `getSuiteStats` (arriba)
 * es un helper interno que devuelve un objeto plano —lo usa `getSuites()`
 * para anexar `metadata.stats`— y nunca llama a `res.json()`. Estaba montado
 * tal cual en `router.get('/stats', getSuiteStats)`, así que Express lo
 * invocaba como `getSuiteStats(req, res, next)`: el parámetro `query` recibía
 * el objeto `req` completo (con referencias circulares) como filtro de
 * Mongo, rompiendo el `$match`, y aunque no rompiera, la función jamás envía
 * una respuesta. El endpoint `GET /api/suites/stats` no respondía nunca.
 * Este wrapper sí es un handler real: llama al helper con un filtro vacío y
 * responde con `res.json`.
 * @route   GET /api/suites/stats
 * @access  Public
 */
export const getSuiteStatsRoute = async (req, res) => {
  try {
    const stats = await getSuiteStats({});
    res.json({ success: true, data: stats });
  } catch (error) {
    console.error('[GetSuiteStatsRoute Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener estadísticas de suites'
    });
  }
};

/**
 * @desc    Obtener todos los amenities disponibles
 * @route   GET /api/suites/amenities
 * @access  Public
 */
export const getAvailableAmenities = async () => {
  try {
    const result = await Suite.aggregate([
      { $unwind: '$amenities' },
      { $group: { _id: '$amenities', count: { $sum: 1 } } },
      { $sort: { count: -1 } },
      { $project: { name: '$_id', count: 1, _id: 0 } }
    ]);
    
    return result;
    
  } catch (error) {
    console.error('[GetAmenities Error]:', error);
    return [];
  }
};

/**
 * @desc    Obtener todos los tipos de suite disponibles
 * @route   GET /api/suites/types
 * @access  Public
 */
export const getSuiteTypes = async (req, res) => {
  try {
    const types = await Suite.aggregate([
      { $match: { available: true } },
      { $group: { _id: '$type', count: { $sum: 1 }, minPrice: { $min: '$price' } } },
      { $sort: { minPrice: 1 } },
      { $project: { name: '$_id', count: 1, minPrice: 1, _id: 0 } }
    ]);
    
    res.json({
      success: true,
      data: types
    });
    
  } catch (error) {
    console.error('[GetSuiteTypes Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Calcular precio para fechas específicas
 * @route   POST /api/suites/calculate-price
 * @access  Public
 */
export const calculatePrice = async (req, res) => {
  try {
    const { suiteId, checkIn, checkOut, includeExperiences, experienceIds } = req.body;
    
    if (!suiteId || !checkIn || !checkOut) {
      return res.status(400).json({
        success: false,
        message: 'Se requieren suiteId, checkIn y checkOut'
      });
    }
    
    const suite = await Suite.findById(suiteId);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    const checkInDate = new Date(checkIn);
    const checkOutDate = new Date(checkOut);
    const nights = Math.ceil((checkOutDate - checkInDate) / (1000 * 60 * 60 * 24));
    
    if (nights <= 0) {
      return res.status(400).json({
        success: false,
        message: 'La fecha de check-out debe ser posterior al check-in'
      });
    }
    
    // Precio base con temporada
    const season = getSeason(checkInDate);
    const nightlyPrice = calculateSeasonalPrice(suite.basePrice, season);
    let subtotal = nightlyPrice * nights;
    
    let experiencesTotal = 0;
    let experiences = [];
    
    // Incluir experiencias si se solicitaron
    if (includeExperiences && experienceIds && experienceIds.length > 0) {
      const Experience = (await import('../models/Experience.js')).default;
      experiences = await Experience.find({ _id: { $in: experienceIds }, available: true });
      experiencesTotal = experiences.reduce((sum, exp) => sum + exp.price, 0);
    }
    
    const total = subtotal + experiencesTotal;
    
    // Descuentos por estadía larga
    let discount = 0;
    let discountReason = null;
    if (nights >= 7) {
      discount = total * 0.1; // 10% descuento
      discountReason = 'Descuento por estadía de 7+ noches (10%)';
    } else if (nights >= 5) {
      discount = total * 0.05; // 5% descuento
      discountReason = 'Descuento por estadía de 5+ noches (5%)';
    }
    
    const finalTotal = total - discount;
    
    res.json({
      success: true,
      data: {
        suite: {
          id: suite._id,
          name: suite.name,
          type: suite.type
        },
        dates: {
          checkIn: checkInDate,
          checkOut: checkOutDate,
          nights
        },
        season,
        nightlyPrice,
        subtotal,
        experiences: experiences.map(e => ({
          id: e._id,
          name: e.name,
          price: e.price
        })),
        experiencesTotal,
        discount,
        discountReason: discount > 0 ? discountReason : null,
        total: finalTotal,
        pricePerNight: Math.round(finalTotal / nights)
      }
    });
    
  } catch (error) {
    console.error('[CalculatePrice Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

// ============================================
// ADMIN CONTROLLERS
// ============================================

/**
 * @desc    Crear una nueva suite (Admin)
 * @route   POST /api/suites
 * @access  Private (Admin)
 */
export const createSuite = async (req, res) => {
  try {
    const {
      name,
      type,
      // Se acepta "price" como alias por compatibilidad con integraciones
      // antiguas, pero el campo real del modelo es "basePrice".
      basePrice = req.body.price,
      originalPrice,
      size,
      description,
      mainImage = req.body.image,
      images,
      amenities,
      features,
      maxGuests,
      totalUnits,
      order
    } = req.body;

    // Validar campos requeridos según el esquema real de Suite
    // (name, type, basePrice, description y size son obligatorios; antes
    // se comprobaban name/type/price, pero el modelo exige description y
    // size, así que la creación fallaba igualmente con un error 500 poco
    // claro de validación de Mongoose).
    const missing = [];
    if (!name) missing.push('name');
    if (!type) missing.push('type');
    if (!basePrice) missing.push('basePrice');
    if (!description) missing.push('description');
    if (size === undefined || size === null) missing.push('size');
    if (missing.length) {
      return res.status(400).json({
        success: false,
        message: `Faltan campos requeridos: ${missing.join(', ')}`
      });
    }

    const suite = new Suite({
      name,
      type,
      basePrice,
      originalPrice: originalPrice || basePrice,
      size,
      description,
      mainImage: mainImage || 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
      images: images || [],
      amenities: amenities || [],
      features: features || [],
      maxGuests: maxGuests || 2,
      totalUnits: totalUnits || 1,
      order: order || 0,
      available: true
    });
    
    await suite.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.status(201).json({
      success: true,
      message: 'Suite creada exitosamente',
      data: suite
    });
    
  } catch (error) {
    console.error('[CreateSuite Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Actualizar una suite (Admin)
 * @route   PUT /api/suites/:id
 * @access  Private (Admin)
 */
export const updateSuite = async (req, res) => {
  try {
    const { id } = req.params;
    
    if (!isValidObjectId(id)) {
      return res.status(400).json({
        success: false,
        message: 'ID de suite inválido'
      });
    }
    
    const suite = await Suite.findById(id);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    // Campos que no se pueden actualizar
    delete req.body._id;
    delete req.body.createdAt;
    
    Object.assign(suite, req.body);
    await suite.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.json({
      success: true,
      message: 'Suite actualizada exitosamente',
      data: suite
    });
    
  } catch (error) {
    console.error('[UpdateSuite Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Eliminar una suite (Admin)
 * @route   DELETE /api/suites/:id
 * @access  Private (Admin)
 */
export const deleteSuite = async (req, res) => {
  try {
    const { id } = req.params;
    
    if (!isValidObjectId(id)) {
      return res.status(400).json({
        success: false,
        message: 'ID de suite inválido'
      });
    }
    
    const suite = await Suite.findById(id);
    if (!suite) {
      return res.status(404).json({
        success: false,
        message: 'Suite no encontrada'
      });
    }
    
    // Soft delete
    suite.available = false;
    await suite.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.json({
      success: true,
      message: 'Suite eliminada exitosamente'
    });
    
  } catch (error) {
    console.error('[DeleteSuite Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};