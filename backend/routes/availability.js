import express from 'express';
import { query } from 'express-validator';
import {
  checkAvailability,
  getMonthlyAvailability,
  getMonthlyPromotions,
  getPeakDates,
  getAvailabilityStats
} from '../controllers/availabilityController.js';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';
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

  query('branch')
    .optional()
    .matches(/^[a-zA-Z0-9-]{1,60}$/).withMessage('Sucursal inválida'),

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

const monthlyAvailabilityValidation = [
  query('year')
    .notEmpty().withMessage('El año es obligatorio')
    .isInt({ min: 2020, max: 2030 }).withMessage('Año inválido'),

  query('month')
    .notEmpty().withMessage('El mes es obligatorio')
    .isInt({ min: 1, max: 12 }).withMessage('Mes inválido (1-12)'),

  query('branch')
    .optional()
    .matches(/^[a-zA-Z0-9-]{1,60}$/).withMessage('Sucursal inválida')
];

// ============================================
// MIDDLEWARE DE CACHÉ
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
// RUTAS
// ============================================

// Disponibilidad de suites para un rango de fechas (buscador y reserva)
router.get(
  '/',
  availabilityValidation,
  validateRequest,
  withCache(30),
  checkAvailability
);

// Ocupación por día de un mes (calendario)
router.get(
  '/monthly',
  monthlyAvailabilityValidation,
  validateRequest,
  withCache(60),
  getMonthlyAvailability
);

// Descuentos vigentes en un mes (calendario)
router.get(
  '/promotions',
  monthlyAvailabilityValidation,
  validateRequest,
  withCache(60),
  getMonthlyPromotions
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

export default router;
