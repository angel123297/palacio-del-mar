import Experience from '../models/Experience.js';

// ============================================
// CONFIGURACIÓN
// ============================================

const DEFAULT_PAGE = 1;
const DEFAULT_LIMIT = 10;
const MAX_LIMIT = 50;
const CACHE_TTL = 300; // 5 minutos en caché (para producción usar Redis)

// Caché simple en memoria (para producción usar Redis)
let cache = new Map();

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Limpia caracteres peligrosos para evitar inyección
 */
const sanitizeString = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str.replace(/[<>{}[\]$?;]/g, '');
};

/**
 * Escapa metacaracteres de regex antes de usar un string en un $regex de
 * MongoDB (SEC-020). Ver el mismo helper en suiteController.js: evita el
 * ReDoS por backtracking catastrófico con patrones tipo "(a+)+b".
 */
const escapeRegex = (str) => {
  if (!str || typeof str !== 'string') return '';
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
    minDuration,
    maxDuration,
    category,
    search,
    available
  } = query;
  
  const validated = {};
  
  // Paginación
  validated.page = Math.max(1, parseInt(page) || DEFAULT_PAGE);
  validated.limit = Math.min(MAX_LIMIT, Math.max(1, parseInt(limit) || DEFAULT_LIMIT));
  
  // Ordenamiento
  const validSortFields = ['price', 'duration', 'name', 'createdAt', 'maxCapacity'];
  validated.sortBy = validSortFields.includes(sortBy) ? sortBy : 'order';
  validated.sortOrder = sortOrder === 'desc' ? -1 : 1;
  
  // Filtros de precio
  validated.minPrice = minPrice ? Math.max(0, parseInt(minPrice)) : null;
  validated.maxPrice = maxPrice ? Math.max(0, parseInt(maxPrice)) : null;
  
  if (validated.minPrice !== null && validated.maxPrice !== null && validated.minPrice > validated.maxPrice) {
    throw new Error('El precio mínimo no puede ser mayor al precio máximo');
  }
  
  // Filtros de duración (en horas)
  validated.minDuration = minDuration ? Math.max(0, parseInt(minDuration)) : null;
  validated.maxDuration = maxDuration ? Math.max(0, parseInt(maxDuration)) : null;
  
  // Categoría
  const validCategories = ['aventura', 'gastronomía', 'cultural', 'relajación', 'tour'];
  validated.category = validCategories.includes(category) ? category : null;
  
  // Búsqueda
  validated.search = search ? sanitizeString(search).trim() : null;
  
  // Disponibilidad
  validated.available = available === 'false' ? false : true;
  
  return validated;
};

/**
 * Construye el query de MongoDB basado en los filtros
 */
const buildQuery = (filters) => {
  const query = {};
  
  // Filtro de disponibilidad
  if (filters.available !== undefined) {
    query.available = filters.available;
  }
  
  // Filtro de precio
  if (filters.minPrice !== null || filters.maxPrice !== null) {
    query.price = {};
    if (filters.minPrice !== null) query.price.$gte = filters.minPrice;
    if (filters.maxPrice !== null) query.price.$lte = filters.maxPrice;
  }
  
  // Filtro de duración
  if (filters.minDuration !== null || filters.maxDuration !== null) {
    query.durationHours = {};
    if (filters.minDuration !== null) query.durationHours.$gte = filters.minDuration;
    if (filters.maxDuration !== null) query.durationHours.$lte = filters.maxDuration;
  }
  
  // Filtro de categoría
  if (filters.category) {
    query.category = filters.category;
  }
  
  // Búsqueda por texto
  if (filters.search) {
    const safeSearch = escapeRegex(filters.search);
    query.$or = [
      { name: { $regex: safeSearch, $options: 'i' } },
      { description: { $regex: safeSearch, $options: 'i' } },
      { shortDescription: { $regex: safeSearch, $options: 'i' } }
    ];
  }
  
  return query;
};

/**
 * Genera una clave de caché única para la consulta
 */
const getCacheKey = (params) => {
  return `experiences:${JSON.stringify(params)}`;
};

/**
 * Invalida la caché de experiencias
 */
const invalidateCache = () => {
  cache.clear();
  console.log('[Cache] Caché de experiencias invalidada');
};

// ============================================
// MAIN CONTROLLERS
// ============================================

/**
 * @desc    Obtener todas las experiencias con filtros y paginación
 * @route   GET /api/experiences
 * @access  Public
 */
export const getExperiences = async (req, res) => {
  const requestId = Math.random().toString(36).substring(7);
  const startTime = Date.now();
  
  try {
    // 1. Validar parámetros
    const filters = validateQueryParams(req.query);
    const { page, limit, sortBy, sortOrder } = filters;
    const skip = (page - 1) * limit;
    
    // 2. Construir query
    const query = buildQuery(filters);
    
    // 3. Verificar caché (opcional, para producción usar Redis)
    const cacheKey = getCacheKey({ ...filters, page, limit });
    if (process.env.ENABLE_CACHE === 'true') {
      const cached = cache.get(cacheKey);
      if (cached && (Date.now() - cached.timestamp) < CACHE_TTL * 1000) {
        console.log(`[Cache] HIT ${requestId} | ${cacheKey.substring(0, 30)}...`);
        return res.json(cached.data);
      }
    }
    
    // 4. Ejecutar consultas en paralelo
    const [experiences, total] = await Promise.all([
      Experience.find(query)
        .sort({ [sortBy]: sortOrder })
        .skip(skip)
        .limit(limit)
        .lean(), // lean() para mejor rendimiento
      Experience.countDocuments(query)
    ]);
    
    // 5. Calcular estadísticas (opcional)
    const stats = await getExperienceStats(query);
    
    // 6. Preparar respuesta
    const response = {
      success: true,
      data: experiences,
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
          category: filters.category,
          search: filters.search
        },
        stats,
        responseTime: Date.now() - startTime,
        requestId
      }
    };
    
    // 7. Guardar en caché
    if (process.env.ENABLE_CACHE === 'true') {
      cache.set(cacheKey, {
        data: response,
        timestamp: Date.now()
      });
      
      // Limpiar caché vieja si es necesario
      if (cache.size > 100) {
        const oldestKey = cache.keys().next().value;
        cache.delete(oldestKey);
      }
    }
    
    res.json(response);
    
  } catch (error) {
    console.error(`[Experiences Error ${requestId}]:`, error);
    
    if (error.message.includes('precio mínimo')) {
      return res.status(400).json({
        success: false,
        message: error.message
      });
    }
    
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor al obtener las experiencias',
      requestId
    });
  }
};

/**
 * @desc    Obtener una experiencia por ID
 * @route   GET /api/experiences/:id
 * @access  Public
 */
export const getExperienceById = async (req, res) => {
  try {
    const { id } = req.params;
    
    if (!id || id.length !== 24) {
      return res.status(400).json({
        success: false,
        message: 'ID de experiencia inválido'
      });
    }
    
    const experience = await Experience.findById(id);
    
    if (!experience) {
      return res.status(404).json({
        success: false,
        message: 'Experiencia no encontrada'
      });
    }
    
    // Obtener experiencias relacionadas (misma categoría)
    const related = await Experience.find({
      category: experience.category,
      _id: { $ne: id },
      available: true
    })
    .limit(3)
    .select('name price durationHours image');
    
    res.json({
      success: true,
      data: experience,
      related
    });
    
  } catch (error) {
    console.error('[GetExperienceById Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener categorías de experiencias
 * @route   GET /api/experiences/categories
 * @access  Public
 */
export const getExperienceCategories = async (req, res) => {
  try {
    const categories = await Experience.aggregate([
      {
        $group: {
          _id: '$category',
          count: { $sum: 1 },
          experiences: { $push: '$$ROOT' }
        }
      },
      {
        $project: {
          category: '$_id',
          count: 1,
          minPrice: { $min: '$experiences.price' },
          maxPrice: { $max: '$experiences.price' },
          sampleImage: { $arrayElemAt: ['$experiences.image', 0] }
        }
      },
      { $sort: { category: 1 } }
    ]);
    
    res.json({
      success: true,
      data: categories
    });
    
  } catch (error) {
    console.error('[GetCategories Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener estadísticas de experiencias
 * @route   GET /api/experiences/stats
 * @access  Public
 */
export const getExperienceStats = async (query = {}) => {
  try {
    const [stats, priceRange] = await Promise.all([
      Experience.aggregate([
        { $match: query },
        {
          $group: {
            _id: null,
            total: { $sum: 1 },
            avgPrice: { $avg: '$price' },
            minPrice: { $min: '$price' },
            maxPrice: { $max: '$price' },
            avgDuration: { $avg: '$durationHours' },
            categories: { $addToSet: '$category' }
          }
        }
      ]),
      Experience.aggregate([
        { $match: { available: true } },
        {
          $group: {
            _id: null,
            min: { $min: '$price' },
            max: { $max: '$price' }
          }
        }
      ])
    ]);
    
    return {
      total: stats[0]?.total || 0,
      averagePrice: Math.round(stats[0]?.avgPrice || 0),
      minPrice: stats[0]?.minPrice || 0,
      maxPrice: stats[0]?.maxPrice || 0,
      averageDuration: Math.round(stats[0]?.avgDuration || 0),
      categories: stats[0]?.categories || [],
      globalPriceRange: {
        min: priceRange[0]?.min || 0,
        max: priceRange[0]?.max || 0
      }
    };
    
  } catch (error) {
    console.error('[GetStats Error]:', error);
    return {
      total: 0,
      averagePrice: 0,
      minPrice: 0,
      maxPrice: 0,
      averageDuration: 0,
      categories: [],
      globalPriceRange: { min: 0, max: 0 }
    };
  }
};

/**
 * @desc    Endpoint HTTP de estadísticas de experiencias.
 *
 * Mismo bug que en `suiteController.js`: `getExperienceStats` (arriba) es un
 * helper interno que devuelve un objeto plano —lo usa `getExperiences()`
 * para anexar `metadata.stats`— y nunca llama a `res.json()`. Montado tal
 * cual en la ruta, `GET /api/experiences/stats` no respondía nunca. Este
 * wrapper sí es un handler real.
 * @route   GET /api/experiences/stats
 * @access  Public
 */
export const getExperienceStatsRoute = async (req, res) => {
  try {
    const stats = await getExperienceStats({});
    res.json({ success: true, data: stats });
  } catch (error) {
    console.error('[GetExperienceStatsRoute Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error al obtener estadísticas de experiencias'
    });
  }
};

// ============================================
// ADMIN CONTROLLERS (protegidos)
// ============================================

/**
 * @desc    Crear una nueva experiencia (Admin)
 * @route   POST /api/experiences
 * @access  Private (Admin)
 */
export const createExperience = async (req, res) => {
  try {
    const {
      name,
      shortDescription,
      description,
      price,
      durationHours,
      durationText,
      category,
      // "image" se acepta como alias de "mainImage" por compatibilidad.
      mainImage = req.body.image,
      images,
      maxCapacity,
      included,
      requirements,
      location,
      icon,
      order
    } = req.body;

    // Validar campos requeridos según el esquema real de Experience.
    if (!name || !description || !price || !durationHours) {
      return res.status(400).json({
        success: false,
        message: 'Faltan campos requeridos: name, description, price, durationHours'
      });
    }

    // El esquema exige "location.name" (un objeto), no un texto suelto;
    // y "schedule" no existe como campo (el esquema usa "schedules", un
    // array de horarios estructurados) — antes se guardaba en un campo
    // que Mongoose simplemente ignoraba.
    const locationObj = typeof location === 'string' || !location
      ? { name: location || 'Palacio del Mar' }
      : { name: location.name || 'Palacio del Mar', ...location };

    const experience = new Experience({
      name,
      shortDescription: shortDescription || description.substring(0, 190),
      description,
      price,
      durationHours,
      durationText: durationText || `${durationHours} horas`,
      category: category || 'tour',
      mainImage: mainImage || 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800',
      images: images || [],
      maxCapacity: maxCapacity || 20,
      included: included || [],
      requirements: requirements || [],
      location: locationObj,
      icon: icon || '✨',
      order: order || 0,
      available: true
    });
    
    await experience.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.status(201).json({
      success: true,
      message: 'Experiencia creada exitosamente',
      data: experience
    });
    
  } catch (error) {
    console.error('[CreateExperience Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Actualizar una experiencia (Admin)
 * @route   PUT /api/experiences/:id
 * @access  Private (Admin)
 */
export const updateExperience = async (req, res) => {
  try {
    const { id } = req.params;
    const updates = req.body;
    
    const experience = await Experience.findById(id);
    if (!experience) {
      return res.status(404).json({
        success: false,
        message: 'Experiencia no encontrada'
      });
    }
    
    // Campos que no se pueden actualizar
    delete updates._id;
    delete updates.createdAt;
    
    Object.assign(experience, updates);
    await experience.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.json({
      success: true,
      message: 'Experiencia actualizada exitosamente',
      data: experience
    });
    
  } catch (error) {
    console.error('[UpdateExperience Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Eliminar una experiencia (Admin)
 * @route   DELETE /api/experiences/:id
 * @access  Private (Admin)
 */
export const deleteExperience = async (req, res) => {
  try {
    const { id } = req.params;
    
    const experience = await Experience.findById(id);
    if (!experience) {
      return res.status(404).json({
        success: false,
        message: 'Experiencia no encontrada'
      });
    }
    
    // Soft delete (marcar como no disponible)
    experience.available = false;
    await experience.save();
    
    // Invalidar caché
    invalidateCache();
    
    res.json({
      success: true,
      message: 'Experiencia eliminada exitosamente'
    });
    
  } catch (error) {
    console.error('[DeleteExperience Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Obtener experiencias destacadas
 * @route   GET /api/experiences/featured
 * @access  Public
 */
export const getFeaturedExperiences = async (req, res) => {
  try {
    const { limit = 4 } = req.query;
    
    const experiences = await Experience.find({ available: true, featured: true })
      .sort({ order: 1, createdAt: -1 })
      .limit(parseInt(limit));
    
    res.json({
      success: true,
      data: experiences
    });
    
  } catch (error) {
    console.error('[GetFeatured Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};

/**
 * @desc    Verificar disponibilidad de una experiencia
 * @route   GET /api/experiences/:id/availability
 * @access  Public
 */
export const checkExperienceAvailability = async (req, res) => {
  try {
    const { id } = req.params;
    const { date, participants } = req.query;
    
    const experience = await Experience.findById(id);
    if (!experience) {
      return res.status(404).json({
        success: false,
        message: 'Experiencia no encontrada'
      });
    }
    
    if (!experience.available) {
      return res.json({
        success: true,
        available: false,
        reason: 'Experiencia no disponible actualmente'
      });
    }
    
    const numParticipants = parseInt(participants) || 1;
    const available = numParticipants <= experience.maxCapacity;
    
    res.json({
      success: true,
      data: {
        available,
        maxCapacity: experience.maxCapacity,
        requestedParticipants: numParticipants,
        remainingSpots: available ? experience.maxCapacity - numParticipants : 0
      }
    });
    
  } catch (error) {
    console.error('[CheckAvailability Error]:', error);
    res.status(500).json({
      success: false,
      message: 'Error interno del servidor'
    });
  }
};