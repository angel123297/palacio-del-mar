import express from 'express';
import { body, param } from 'express-validator';
import { authMiddleware } from '../middleware/auth.js';
import { validateRequest } from '../middleware/validate.js';
import { createRateLimiter } from '../middleware/rateLimit.js';
import { checkout, getPaymentConfig, listBookingPayments, getUserPaymentHistory } from '../controllers/paymentController.js';

const router = express.Router();

// 20 intentos de pago por usuario cada 10 minutos
const checkoutLimiter = createRateLimiter({
  windowMs: 10 * 60 * 1000,
  max: 20,
  message: 'Demasiados intentos de pago. Espera unos minutos.'
});

router.get('/config', getPaymentConfig);
router.get('/history', authMiddleware, getUserPaymentHistory);

router.post(
  '/checkout',
  authMiddleware,
  checkoutLimiter,
  body('bookingId').isMongoId().withMessage('Reserva inválida'),
  body('method').isString().withMessage('Método de pago requerido'),
  body('idempotencyKey').isString().withMessage('Clave de idempotencia requerida'),
  validateRequest,
  checkout
);

router.get(
  '/booking/:bookingId',
  authMiddleware,
  param('bookingId').isMongoId().withMessage('Reserva inválida'),
  validateRequest,
  listBookingPayments
);

export default router;
