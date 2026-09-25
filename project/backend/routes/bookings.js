import express from 'express';
import { body, param, query } from 'express-validator';
import { 
  createBooking, 
  getUserBookings, 
  getBookingById,
  cancelBooking,
  modifyBookingDates,
  updatePaymentStatus,
  getAllBookings,
  getBookingStats,
  getUpcomingBookings,
  addExperienceToBooking,
  removeExperienceFromBooking,
  sendBookingConfirmation
} from '../controllers/bookingController.js';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';
import { validateRequest } from '../middleware/validate.js';

const router = express.Router();

// ============================================
// VALIDACIONES
// ============================================

const createBookingValidation = [
  body('suiteId')
    .notEmpty().withMessage('El ID de la suite es obligatorio')
    .isMongoId().withMessage('ID de suite inválido'),
  
  body('checkIn')
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
  
  body('checkOut')
    .notEmpty().withMessage('La fecha de check-out es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido (YYYY-MM-DD)')
    .custom((value, { req }) => {
      const checkIn = new Date(req.body.checkIn);
      const checkOut = new Date(value);
      if (checkOut <= checkIn) {
        throw new Error('La fecha de check-out debe ser posterior al check-in');
      }
      
      const maxNights = 90;
      const nights = Math.ceil((checkOut - checkIn) / (1000 * 60 * 60 * 24));
      if (nights > maxNights) {
        throw new Error(`La estadía no puede exceder ${maxNights} noches`);
      }
      return true;
    }),
  
  body('guests')
    .notEmpty().withMessage('El número de huéspedes es obligatorio')
    .isInt({ min: 1, max: 20 }).withMessage('El número de huéspedes debe estar entre 1 y 20'),
  
  body('experiences')
    .optional()
    .isArray().withMessage('Experiencias debe ser un array')
    .custom(value => {
      if (value && value.length > 5) {
        throw new Error('No se pueden agregar más de 5 experiencias');
      }
      return true;
    }),
  
  body('guestName')
    .optional()
    .trim()
    .isLength({ min: 2, max: 100 }).withMessage('El nombre debe tener entre 2 y 100 caracteres'),
  
  body('guestEmail')
    .optional()
    .isEmail().withMessage('Email inválido')
    .normalizeEmail(),
  
  body('guestPhone')
    .optional()
    .matches(/^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}$/)
    .withMessage('Teléfono inválido'),
  
  body('specialRequests')
    .optional()
    .isLength({ max: 500 }).withMessage('Las solicitudes especiales no pueden exceder 500 caracteres')
];

const cancelBookingValidation = [
  param('id')
    .notEmpty().withMessage('El ID de la reserva es obligatorio')
    .isMongoId().withMessage('ID de reserva inválido'),
  
  body('reason')
    .optional()
    .trim()
    .isLength({ max: 500 }).withMessage('El motivo no puede exceder 500 caracteres')
];

const modifyDatesValidation = [
  param('id')
    .notEmpty().withMessage('El ID de la reserva es obligatorio')
    .isMongoId().withMessage('ID de reserva inválido'),
  
  body('newCheckIn')
    .notEmpty().withMessage('La nueva fecha de check-in es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido'),
  
  body('newCheckOut')
    .notEmpty().withMessage('La nueva fecha de check-out es obligatoria')
    .isISO8601().withMessage('Formato de fecha inválido')
    .custom((value, { req }) => {
      const checkIn = new Date(req.body.newCheckIn);
      const checkOut = new Date(value);
      if (checkOut <= checkIn) {
        throw new Error('La fecha de check-out debe ser posterior al check-in');
      }
      return true;
    })
];

const paymentStatusValidation = [
  param('id')
    .notEmpty().withMessage('El ID de la reserva es obligatorio')
    .isMongoId().withMessage('ID de reserva inválido'),
  
  body('paymentStatus')
    .notEmpty().withMessage('El estado de pago es obligatorio')
    .isIn(['pending', 'paid', 'failed', 'refunded', 'partial'])
    .withMessage('Estado de pago inválido'),
  
  body('transactionId')
    .optional()
    .trim(),
  
  // Importe cobrado (obligatorio para 'partial') o reembolsado (opcional en 'refunded')
  body('amount')
    .optional({ values: 'falsy' })
    .isFloat({ gt: 0 }).withMessage('El importe debe ser un número mayor que 0')
];

const getBookingsValidation = [
  query('page')
    .optional()
    .isInt({ min: 1 }).withMessage('La página debe ser un número positivo'),
  
  query('limit')
    .optional()
    .isInt({ min: 1, max: 50 }).withMessage('El límite debe estar entre 1 y 50'),
  
  query('status')
    .optional()
    .isIn(['pending', 'confirmed', 'cancelled', 'completed', 'no_show'])
    .withMessage('Estado inválido'),
  
  query('startDate')
    .optional()
    .isISO8601().withMessage('Fecha de inicio inválida'),
  
  query('endDate')
    .optional()
    .isISO8601().withMessage('Fecha de fin inválida')
];

const addExperienceValidation = [
  param('id')
    .notEmpty().withMessage('El ID de la reserva es obligatorio')
    .isMongoId().withMessage('ID de reserva inválido'),
  
  body('experienceId')
    .notEmpty().withMessage('El ID de la experiencia es obligatorio')
    .isMongoId().withMessage('ID de experiencia inválido')
];

// ============================================
// RUTAS PROTEGIDAS (Requieren autenticación)
// ============================================

/**
 * @route   POST /api/bookings
 * @desc    Crear una nueva reserva
 * @access  Private
 * @body    {string} suiteId - ID de la suite
 * @body    {string} checkIn - Fecha de check-in (YYYY-MM-DD)
 * @body    {string} checkOut - Fecha de check-out (YYYY-MM-DD)
 * @body    {number} guests - Número de huéspedes
 * @body    {array} experiences - IDs de experiencias (opcional)
 * @body    {string} guestName - Nombre del huésped (opcional)
 * @body    {string} guestEmail - Email del huésped (opcional)
 * @body    {string} guestPhone - Teléfono (opcional)
 * @body    {string} specialRequests - Solicitudes especiales (opcional)
 */
router.post('/', authMiddleware, createBookingValidation, validateRequest, createBooking);

/**
 * @route   GET /api/bookings
 * @desc    Obtener todas las reservas del usuario autenticado
 * @access  Private
 * @query   {number} page - Número de página (default: 1)
 * @query   {number} limit - Límite por página (default: 10, max: 50)
 * @query   {string} status - Filtrar por estado
 * @query   {string} startDate - Filtrar por fecha de inicio
 * @query   {string} endDate - Filtrar por fecha de fin
 */
router.get('/', authMiddleware, getBookingsValidation, validateRequest, getUserBookings);

/**
 * @route   GET /api/bookings/upcoming
 * @desc    Obtener reservas próximas del usuario
 * @access  Private
 * @query   {number} days - Días hacia adelante (default: 30)
 */
router.get(
  '/upcoming',
  authMiddleware,
  query('days').optional().isInt({ min: 1, max: 365 }).withMessage('Días inválido'),
  validateRequest,
  getUpcomingBookings
);

/**
 * @route   GET /api/bookings/:id
 * @desc    Obtener una reserva por ID
 * @access  Private
 * @param   {string} id - ID de la reserva
 */
router.get(
  '/:id',
  authMiddleware,
  param('id').isMongoId().withMessage('ID de reserva inválido'),
  validateRequest,
  getBookingById
);

/**
 * @route   PUT /api/bookings/:id/cancel
 * @desc    Cancelar una reserva
 * @access  Private
 * @param   {string} id - ID de la reserva
 * @body    {string} reason - Motivo de cancelación (opcional)
 */
router.put(
  '/:id/cancel',
  authMiddleware,
  cancelBookingValidation,
  validateRequest,
  cancelBooking
);

/**
 * @route   PUT /api/bookings/:id/modify-dates
 * @desc    Modificar fechas de una reserva
 * @access  Private
 * @param   {string} id - ID de la reserva
 * @body    {string} newCheckIn - Nueva fecha de check-in
 * @body    {string} newCheckOut - Nueva fecha de check-out
 */
router.put(
  '/:id/modify-dates',
  authMiddleware,
  modifyDatesValidation,
  validateRequest,
  modifyBookingDates
);

/**
 * @route   POST /api/bookings/:id/experiences
 * @desc    Agregar experiencia a una reserva existente
 * @access  Private
 * @param   {string} id - ID de la reserva
 * @body    {string} experienceId - ID de la experiencia
 */
router.post(
  '/:id/experiences',
  authMiddleware,
  addExperienceValidation,
  validateRequest,
  addExperienceToBooking
);

/**
 * @route   DELETE /api/bookings/:id/experiences/:experienceId
 * @desc    Eliminar experiencia de una reserva
 * @access  Private
 * @param   {string} id - ID de la reserva
 * @param   {string} experienceId - ID de la experiencia
 */
router.delete(
  '/:id/experiences/:experienceId',
  authMiddleware,
  param('id').isMongoId().withMessage('ID de reserva inválido'),
  param('experienceId').isMongoId().withMessage('ID de experiencia inválido'),
  validateRequest,
  removeExperienceFromBooking
);

/**
 * @route   POST /api/bookings/:id/confirm
 * @desc    Reenviar confirmación de reserva por email
 * @access  Private
 * @param   {string} id - ID de la reserva
 */
router.post(
  '/:id/confirm',
  authMiddleware,
  param('id').isMongoId().withMessage('ID de reserva inválido'),
  validateRequest,
  sendBookingConfirmation
);

// ============================================
// RUTAS DE ADMINISTRADOR
// ============================================

/**
 * @route   GET /api/bookings/admin/all
 * @desc    Obtener todas las reservas (administrador)
 * @access  Private (Admin)
 * @query   {number} page - Número de página
 * @query   {number} limit - Límite por página
 * @query   {string} status - Filtrar por estado
 * @query   {string} startDate - Filtrar por fecha de inicio
 * @query   {string} endDate - Filtrar por fecha de fin
 * @query   {string} userId - Filtrar por usuario
 */
router.get(
  '/admin/all',
  authMiddleware,
  adminMiddleware,
  getBookingsValidation,
  validateRequest,
  getAllBookings
);

/**
 * @route   GET /api/bookings/admin/stats
 * @desc    Obtener estadísticas de reservas
 * @access  Private (Admin)
 * @query   {string} startDate - Fecha de inicio (opcional)
 * @query   {string} endDate - Fecha de fin (opcional)
 */
router.get(
  '/admin/stats',
  authMiddleware,
  adminMiddleware,
  query('startDate').optional().isISO8601().withMessage('Fecha de inicio inválida'),
  query('endDate').optional().isISO8601().withMessage('Fecha de fin inválida'),
  validateRequest,
  getBookingStats
);

/**
 * @route   PUT /api/bookings/admin/:id/payment-status
 * @desc    Actualizar estado de pago de una reserva
 * @access  Private (Admin)
 * @param   {string} id - ID de la reserva
 * @body    {string} paymentStatus - Nuevo estado de pago
 * @body    {string} transactionId - ID de transacción (opcional)
 */
router.put(
  '/admin/:id/payment-status',
  authMiddleware,
  adminMiddleware,
  paymentStatusValidation,
  validateRequest,
  updatePaymentStatus
);

// ============================================
// RUTA DE HEALTH CHECK
// ============================================

/**
 * @route   GET /api/bookings/health
 * @desc    Verificar estado del servicio de reservas
 * @access  Public
 */
router.get('/health', (req, res) => {
  res.json({
    success: true,
    message: 'Bookings service is running',
    timestamp: new Date().toISOString(),
    endpoints: {
      user: [
        'POST /',
        'GET /',
        'GET /upcoming',
        'GET /:id',
        'PUT /:id/cancel',
        'PUT /:id/modify-dates',
        'POST /:id/experiences',
        'DELETE /:id/experiences/:experienceId',
        'POST /:id/confirm'
      ],
      admin: [
        'GET /admin/all',
        'GET /admin/stats',
        'PUT /admin/:id/payment-status'
      ]
    }
  });
});

export default router;