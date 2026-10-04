import express from 'express';
import { body, param, query } from 'express-validator';
import { authMiddleware, adminMiddleware } from '../middleware/auth.js';
import { validateRequest } from '../middleware/validate.js';
import {
  listAdminBookings,
  updateBookingStatus,
  listPromotions,
  createPromotion,
  updatePromotion,
  deletePromotion
} from '../controllers/adminController.js';

const router = express.Router();

// Todo lo de /api/admin exige sesión iniciada y rol de administrador
router.use(authMiddleware, adminMiddleware);

// ---------- Reservas ----------
router.get(
  '/bookings',
  query('page').optional().isInt({ min: 1 }).withMessage('Página inválida'),
  query('limit').optional().isInt({ min: 1, max: 100 }).withMessage('Límite inválido'),
  validateRequest,
  listAdminBookings
);

router.put(
  '/bookings/:id/status',
  param('id').isMongoId().withMessage('ID de reserva inválido'),
  body('status').isIn(['confirmed', 'completed', 'no_show', 'cancelled']).withMessage('Estado inválido'),
  body('reason').optional().isString().isLength({ max: 300 }).withMessage('El motivo no puede superar 300 caracteres'),
  validateRequest,
  updateBookingStatus
);

// ---------- Promociones ----------
router.get('/promotions', listPromotions);
router.post('/promotions', createPromotion);
router.put('/promotions/:id', param('id').isMongoId().withMessage('ID de promoción inválido'), validateRequest, updatePromotion);
router.delete('/promotions/:id', param('id').isMongoId().withMessage('ID de promoción inválido'), validateRequest, deletePromotion);

export default router;
