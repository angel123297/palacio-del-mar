// Pago de una reserva pendiente. Recibe los modelos y el proveedor por parámetro
// para poder probarlo sin base de datos (ver tests/payments.test.js).
import { PAYMENT_METHODS, CURRENCY } from './payments/index.js';

export class PaymentError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const KEY_RE = /^[A-Za-z0-9_-]{8,80}$/;
const same = (a, b) => String(a) === String(b);
const receiptFor = (payment, at) =>
  `PM-${at.toISOString().slice(0, 10).replace(/-/g, '')}-${String(payment._id).slice(-6).toUpperCase()}`;

export const createPaymentService = ({ Booking, Payment, provider, now = () => new Date(), notify = async () => {} }) => ({
  /**
   * Cobra una reserva `pending`. Idempotente por (reserva, idempotencyKey).
   * Devuelve { payment, booking, replay }.
   */
  async pay({ bookingId, userId, method, idempotencyKey }) {
    if (!PAYMENT_METHODS.some((m) => m.id === method)) {
      throw new PaymentError(400, 'Método de pago no válido', 'INVALID_METHOD');
    }
    if (!KEY_RE.test(String(idempotencyKey || ''))) {
      throw new PaymentError(400, 'Falta la clave de idempotencia del intento de pago', 'INVALID_KEY');
    }

    const booking = await Booking.findById(bookingId);
    // Una reserva ajena responde igual que una inexistente (no revela que existe)
    if (!booking || !same(booking.user, userId)) {
      throw new PaymentError(404, 'Reserva no encontrada', 'NOT_FOUND');
    }

    // Mismo intento repetido (doble clic, reintento de red): devolver lo ya hecho
    const previous = await Payment.findOne({ booking: booking._id, idempotencyKey });
    if (previous) return { payment: previous, booking, replay: true };

    if (booking.paymentStatus === 'paid') {
      const paid = await Payment.findOne({ booking: booking._id, status: 'approved' });
      if (paid) return { payment: paid, booking, replay: true };
      throw new PaymentError(409, 'Esta reserva ya está pagada', 'ALREADY_PAID');
    }
    if (booking.status === 'expired') {
      throw new PaymentError(409, 'Esta reserva venció porque no se pagó a tiempo. Vuelve a reservar.', 'HOLD_EXPIRED');
    }
    if (booking.status !== 'pending') {
      throw new PaymentError(409, 'Esta reserva ya no se puede pagar', 'NOT_PAYABLE');
    }
    if (booking.holdExpiresAt && new Date(booking.holdExpiresAt) <= now()) {
      throw new PaymentError(409, 'El tiempo para pagar terminó. Vuelve a reservar.', 'HOLD_EXPIRED');
    }

    let payment;
    try {
      payment = await Payment.create({
        booking: booking._id,
        user: userId,
        amount: booking.totalPrice,
        currency: CURRENCY,
        method,
        provider: provider.name,
        status: 'processing',
        idempotencyKey
      });
    } catch (err) {
      if (err?.code === 11000) {
        // Otra petición con la misma clave ganó la carrera
        const winner = await Payment.findOne({ booking: booking._id, idempotencyKey });
        if (winner) return { payment: winner, booking, replay: true };
      }
      throw err;
    }

    const result = await provider.charge({
      amount: booking.totalPrice,
      method,
      bookingId: String(booking._id),
      idempotencyKey
    });

    if (result.status !== 'approved') {
      payment.status = 'declined';
      payment.failureReason = result.message || 'Pago rechazado';
      await payment.save();
      throw new PaymentError(402, payment.failureReason, 'DECLINED');
    }

    // Confirmar la reserva de forma ATÓMICA y solo si sigue pendiente y vigente:
    // si el barrido de vencimiento o un admin llegó primero, no se confirma.
    // (Con una pasarela real, aquí habría que reembolsar el cobro.)
    const paidAt = now();
    const confirmed = await Booking.findOneAndUpdate(
      {
        _id: booking._id,
        user: userId,
        status: 'pending',
        paymentStatus: { $in: ['pending', 'failed'] },
        $or: [{ holdExpiresAt: { $gt: paidAt } }, { holdExpiresAt: { $exists: false } }, { holdExpiresAt: null }]
      },
      {
        $set: {
          status: 'confirmed',
          paymentStatus: 'paid',
          amountPaid: booking.totalPrice,
          paidAt,
          paymentMethod: method,
          transactionId: result.providerRef
        },
        $unset: { holdExpiresAt: 1 }
      },
      { new: true }
    );

    if (!confirmed) {
      payment.status = 'void';
      payment.failureReason = 'La reserva venció o cambió mientras se pagaba';
      await payment.save();
      throw new PaymentError(409, 'La reserva venció mientras pagabas. No se confirmó ni se cobró.', 'HOLD_EXPIRED');
    }

    payment.status = 'approved';
    payment.providerRef = result.providerRef;
    payment.approvedAt = paidAt;
    payment.receiptNumber = receiptFor(payment, paidAt);
    await payment.save();

    Promise.resolve(notify({ booking: confirmed, payment })).catch((err) =>
      console.error('[Pagos] Error enviando el comprobante:', err.message));

    return { payment, booking: confirmed, replay: false };
  }
});
