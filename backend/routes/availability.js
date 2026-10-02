import express from 'express';
import { query, param, body } from 'express-validator'; // ✅ agregado body
import { 
  checkAvailability, 
  checkSuiteAvailability,
  getDynamicPrices,
  getMonthlyAvailability,
  checkBatchAvailability,      // ✅ nombre correcto (era getBatchAvailability)
  getAvailabilityCalendar,
  getPeakDates,
  checkMultipleSuitesAvailability,
  getAvailabilityStats         // ✅ agregado (faltaba en el import)
} from '../controllers/availabilityController.js';
import { authMiddleware, optionalAuthMiddleware, adminMiddleware } from '../middleware/auth.js';
import { validateRequest } from '../middleware/validate.js';

const router = express.Router();

// ============================================
// VALIDACIONES
// ============================================

const availabilityValidation = [
  query('checkIn')
    .notEmpty().withMessage('La fecha de check-in es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido (YYYY-MM-DD)')
    .custom(value => {
      const date = new Date(value);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (date < today) {
        throw new Error('La fecha de check-in no puede ser anterior a hoy');
      }
      return true;
    }),
  
  query('checkOut')
    .notEmpty().withMessage('La fecha de check-out es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido (YYYY-MM-DD)')
    .custom((value, { req }) => {
      const checkIn = new Date(req.query.checkIn);
      const checkOut = new Date(value);
      if (checkOut <= checkIn) {
        throw new Error('La fecha de check-out debe ser posterior al check-in');
      }
      return true;
    }),
  
  query('guests')
    .optional()
    .isInt({ min: 1, max: 20 }).withMessage('El número de huéspedes debe estar entre 1 y 20'),
  
  query('suiteType')
    .optional()
    .isString().withMessage('El tipo de suite debe ser texto')
    .isIn(['Habitación', 'Suite Deluxe', 'Suite Premium', 'Suite Presidencial', 'Suite Exclusiva'])
    .withMessage('Tipo de suite inválido'),
  
  query('minPrice')
    .optional()
    .isInt({ min: 0 }).withMessage('El precio mínimo debe ser un número positivo'),
  
  query('maxPrice')
    .optional()
    .isInt({ min: 0 }).withMessage('El precio máximo debe ser un número positivo')
    .custom((value, { req }) => {
      if (req.query.minPrice && parseInt(value) < parseInt(req.query.minPrice)) {
        throw new Error('El precio máximo debe ser mayor al precio mínimo');
      }
      return true;
    })
];

const suiteAvailabilityValidation = [
  param('suiteId')
    .notEmpty().withMessage('El ID de la suite es obligatorio')
    .isMongoId().withMessage('ID de suite inválido'),
  
  query('checkIn')
    .notEmpty().withMessage('La fecha de check-in es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido (YYYY-MM-DD)'),
  
  query('checkOut')
    .notEmpty().withMessage('La fecha de check-out es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido (YYYY-MM-DD)')
    .custom((value, { req }) => {
      const checkIn = new Date(req.query.checkIn);
      const checkOut = new Date(value);
      if (checkOut <= checkIn) {
        throw new Error('La fecha de check-out debe ser posterior al check-in');
      }
      return true;
    })
];

const monthlyAvailabilityValidation = [
  query('year')
    .notEmpty().withMessage('El año es obligatorio')
    .isInt({ min: 2020, max: 2030 }).withMessage('Año inválido'),
  
  query('month')
    .notEmpty().withMessage('El mes es obligatorio')
    .isInt({ min: 1, max: 12 }).withMessage('Mes inválido (1-12)')
];

const batchAvailabilityValidation = [
  body('requests')
    .isArray({ min: 1, max: 10 }).withMessage('Debe proporcionar entre 1 y 10 solicitudes')
    .custom(value => {
      for (const req of value) {
        if (!req.suiteId || !req.checkIn || !req.checkOut) {
          throw new Error('Cada solicitud debe tener suiteId, checkIn y checkOut');
        }
        const checkIn = new Date(req.checkIn);
        const checkOut = new Date(req.checkOut);
        if (checkOut <= checkIn) {
          throw new Error('Check-out debe ser posterior a check-in');
        }
      }
      return true;
    })
];

const multipleSuitesValidation = [
  body('suiteIds')
    .isArray({ min: 1, max: 5 }).withMessage('Debe proporcionar entre 1 y 5 IDs de suite'),
  
  body('checkIn')
    .notEmpty().withMessage('La fecha de check-in es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido'),
  
  body('checkOut')
    .notEmpty().withMessage('La fecha de check-out es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido')
    .custom((value, { req }) => {
      const checkIn = new Date(req.body.checkIn);
      const checkOut = new Date(value);
      if (checkOut <= checkIn) {
        throw new Error('La fecha de check-out debe ser posterior al check-in');
      }
      return true;
    })
];

// ============================================
// MIDDLEWARE DE CACHÉ (Opcional)
// ============================================

const cache = new Map();

const withCache = (duration = 60) => {
  return (req, res, next) => {
    if (req.method !== 'GET') {
      return next();
    }
    
    const key = `${req.originalUrl || req.url}`;
    const cached = cache.get(key);
    
    if (cached && (Date.now() - cached.timestamp) < duration * 1000) {
      return res.json(cached.data);
    }
    
    const originalSend = res.json;
    res.json = function(data) {
      if (res.statusCode === 200) {
        cache.set(key, {
          data,
          timestamp: Date.now()
        });
      }
      originalSend.call(this, data);
    };
    
    next();
  };
};

// ============================================
// RUTAS PÚBLICAS
// ============================================

router.get(
  '/', 
  availabilityValidation,
  validateRequest,
  withCache(30),
  checkAvailability
);

router.get(
  '/suite/:suiteId',
  suiteAvailabilityValidation,
  validateRequest,
  withCache(30),
  checkSuiteAvailability
);

router.post(
  '/batch',
  batchAvailabilityValidation,
  validateRequest,
  checkBatchAvailability
);

router.post(
  '/multiple',
  multipleSuitesValidation,
  validateRequest,
  checkMultipleSuitesAvailability
);

router.get(
  '/prices',
  query('checkIn').isISO8601().withMessage('Fecha de check-in inválida'),
  query('checkOut').isISO8601().withMessage('Fecha de check-out inválida'),
  query('suiteId').optional().isMongoId().withMessage('ID de suite inválido'),
  validateRequest,
  withCache(300),
  getDynamicPrices
);

router.get(
  '/monthly',
  monthlyAvailabilityValidation,
  validateRequest,
  withCache(60),
  getMonthlyAvailability
);

router.get(
  '/calendar',
  query('suiteId').isMongoId().withMessage('ID de suite inválido'),
  query('year').isInt({ min: 2020, max: 2030 }).withMessage('Año inválido'),
  query('month').isInt({ min: 1, max: 12 }).withMessage('Mes inválido'),
  validateRequest,
  withCache(60),
  getAvailabilityCalendar
);

router.get(
  '/peak-dates',
  query('year').optional().isInt({ min: 2020, max: 2030 }).withMessage('Año inválido'),
  validateRequest,
  getPeakDates
);

// SEC-018: requiere sesión de admin — expone ingresos y ocupación agregados.
router.get(
  '/stats',
  authMiddleware,
  adminMiddleware,
  query('startDate').optional().isISO8601().withMessage('Fecha de inicio inválida'),
  query('endDate').optional().isISO8601().withMessage('Fecha de fin inválida'),
  validateRequest,
  withCache(300),
  getAvailabilityStats
);

router.get('/health', (req, res) => {
  res.json({
    success: true,
    message: 'Availability service is running',
    timestamp: new Date().toISOString(),
    cacheSize: cache.size,
    endpoints: [
      'GET /',
      'GET /suite/:suiteId',
      'POST /batch',
      'POST /multiple',
      'GET /prices',
      'GET /monthly',
      'GET /calendar',
      'GET /peak-dates',
      'GET /stats'
    ]
  });
});

// ============================================
// LIMPIAR CACHÉ (Admin)
// ============================================

router.delete('/cache', authMiddleware, adminMiddleware, (req, res) => {
  cache.clear();
  res.json({
    success: true,
    message: 'Caché limpiado exitosamente'
  });
});

export default router;