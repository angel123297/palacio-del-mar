import Booking from '../models/Booking.js';
import Payment from '../models/Payment.js';
import { createPaymentService, PaymentError } from '../services/paymentService.js';
import { PAYMENT_METHODS, CURRENCY, paymentsMode, getProvider } from '../services/payments/index.js';
import { holdMinutes } from '../utils/bookingRules.js';
import { CANCELLATION_POLICY } from '../utils/pricing.js';
import { sendBookingConfirmationEmail } from '../utils/email.js';

const service = () =>
  createPaymentService({
    Booking,
    Payment,
    provider: getProvider(),
    notify: async ({ booking, payment }) => {
      await booking.populate('suite');
      if (booking.guestEmail) await sendBookingConfirmationEmail(booking, booking.guestEmail, booking.guestName, payment);
    }
  });

const publicPayment = (p) => ({
  id: p._id,
  status: p.status,
  amount: p.amount,
  currency: p.currency,
  method: p.method,
  receiptNumber: p.receiptNumber || null,
  approvedAt: p.approvedAt || null,
  simulated: p.provider === 'simulated'
});

/** GET /api/payments/config (público): lo que el sitio necesita para pintar el pago. */
export const getPaymentConfig = (req, res) => {
  const mode = paymentsMode();
  res.json({
    success: true,
    data: {
      mode,
      simulated: mode === 'simulated',
      currency: CURRENCY,
      methods: PAYMENT_METHODS,
      holdMinutes: holdMinutes(),
      cancellationPolicy: CANCELLATION_POLICY
    }
  });
};

/** POST /api/payments/checkout { bookingId, method, idempotencyKey } */
export const checkout = async (req, res) => {
  try {
    const { bookingId, method, idempotencyKey } = req.body;
    const { payment, booking, replay } = await service().pay({
      bookingId,
      userId: req.user.id,
      method,
      idempotencyKey
    });
    res.status(replay ? 200 : 201).json({
      success: true,
      message: replay ? 'Este pago ya estaba registrado' : 'Pago aprobado',
      data: {
        payment: publicPayment(payment),
        booking: {
          _id: booking._id,
          status: booking.status,
          paymentStatus: booking.paymentStatus,
          totalPrice: booking.totalPrice
        }
      }
    });
  } catch (err) {
    if (err instanceof PaymentError) {
      return res.status(err.status).json({ success: false, message: err.message, code: err.code });
    }
    console.error('[Checkout Error]:', err);
    res.status(500).json({ success: false, message: 'Error interno al procesar el pago' });
  }
};

/** GET /api/payments/booking/:bookingId: pagos de una reserva (dueño o admin). */
export const listBookingPayments = async (req, res) => {
  try {
    const booking = await Booking.findById(req.params.bookingId).select('user');
    if (!booking || (String(booking.user) !== String(req.user.id) && req.user.role !== 'admin')) {
      return res.status(404).json({ success: false, message: 'Reserva no encontrada' });
    }
    const payments = await Payment.find({ booking: booking._id }).sort({ createdAt: -1 });
    res.json({ success: true, data: payments.map(publicPayment) });
  } catch (err) {
    console.error('[ListPayments Error]:', err);
    res.status(500).json({ success: false, message: 'Error interno del servidor' });
  }
};
