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
    if (previous) {
      // Un intento fallido (rechazado, error de pasarela o anulado) NO se "repite":
      // devolverlo haría creer al cliente que pagó. Debe reintentar con otra clave.
      if (['declined', 'void'].includes(previous.status)) {
        throw new PaymentError(409, 'El intento de pago anterior no se completó. Intenta de nuevo.', 'RETRY_NEW_KEY');
      }
      return { payment: previous, booking, replay: true };
    }

    if (booking.paymentStatus === 'paid') {
      const paid = await Payment.findOne({ booking: booking._id, status: 'approved' });
      if (paid) return { payment: paid, booking, replay: true };
      throw new PaymentError(409, 'Esta reserva ya está pagada', 'ALREADY_PAID');
    }
    if (booking.status === 'expired') {
      throw new PaymentError(409, 'Esta reserva venció porque no se pagó a tiempo. Vuelve a reservar.', 'HOLD_EXPIRED');
    }
    if (booking.status !== 'pending' && !(booking.status === 'confirmed' && booking.paymentStatus === 'partial')) {
      throw new PaymentError(409, 'Esta reserva ya no se puede pagar', 'NOT_PAYABLE');
    }
    if (booking.status === 'pending' && booking.holdExpiresAt && new Date(booking.holdExpiresAt) <= now()) {
      throw new PaymentError(409, 'El tiempo para pagar terminó. Vuelve a reservar.', 'HOLD_EXPIRED');
    }

    let alreadyPaid = 0;
    if (booking.paymentStatus === 'partial') {
      if (booking.amountPaid > 0) {
        alreadyPaid = booking.amountPaid;
      } else {
        const approvedPayments = await Payment.find({ booking: booking._id, status: 'approved' });
        alreadyPaid = approvedPayments.reduce((sum, p) => sum + p.amount, 0);
        if (alreadyPaid === 0 && booking.modificationHistory?.length) {
          alreadyPaid = booking.modificationHistory[booking.modificationHistory.length - 1].oldTotalPrice || 0;
        }
      }
    }
    const chargeAmount = Math.max(0, booking.totalPrice - alreadyPaid);
    if (chargeAmount <= 0) {
      throw new PaymentError(409, 'Esta reserva ya está pagada', 'ALREADY_PAID');
    }

    const createPayment = () => Payment.create({
      booking: booking._id,
      user: userId,
      amount: chargeAmount,
      currency: CURRENCY,
      method,
      provider: provider.name,
      status: 'processing',
      active: true,
      idempotencyKey
    });

    let payment;
    try {
      payment = await createPayment();
    } catch (err) {
      if (err?.code !== 11000) throw err;
      // Otra petición con la misma clave ganó la carrera
      const winner = await Payment.findOne({ booking: booking._id, idempotencyKey });
      if (winner) return { payment: winner, booking, replay: true };
      const active = await Payment.findOne({ booking: booking._id, active: true });
      if (active?.status === 'approved') {
        // Pago aprobado ANTERIOR que quedó con active:true (versiones viejas del
        // código). No es un reintento de este cobro: es el pago original, y dejarlo
        // bloqueando impedía cobrar los días extra al modificar fechas.
        // Se libera y se cobra el saldo pendiente.
        await Payment.updateOne({ _id: active._id }, { $unset: { active: 1 } });
        try {
          payment = await createPayment();
        } catch (err2) {
          if (err2?.code === 11000) {
            throw new PaymentError(409, 'Ya hay un pago en proceso para esta reserva. Espera unos segundos.', 'PAYMENT_IN_PROGRESS');
          }
          throw err2;
        }
      } else {
        // Otro intento (clave distinta) ya está cobrando esta reserva
        throw new PaymentError(409, 'Ya hay un pago en proceso para esta reserva. Espera unos segundos.', 'PAYMENT_IN_PROGRESS');
      }
    }

    let result;
    try {
      result = await provider.charge({
        amount: chargeAmount,
        method,
        bookingId: String(booking._id),
        idempotencyKey
      });
    } catch (err) {
      payment.status = 'declined';
      payment.active = undefined; // libera el cupo para reintentar
      payment.failureReason = err.message || 'Error de comunicación con la pasarela de pagos';
      await payment.save().catch(() => {});
      throw new PaymentError(502, payment.failureReason, 'GATEWAY_ERROR');
    }

    if (result.status !== 'approved') {
      payment.status = 'declined';
      payment.active = undefined; // libera el cupo para reintentar
      payment.failureReason = result.message || 'Pago rechazado';
      await payment.save();
      throw new PaymentError(402, payment.failureReason, 'DECLINED');
    }

    // Confirmar la reserva de forma ATÓMICA y solo si sigue pendiente y vigente:
    // si el barrido de vencimiento o un admin llegó primero, no se confirma.
    // (Con una pasarela real, aquí habría que reembolsar el cobro.)
    const paidAt = now();
    const isPartial = booking.paymentStatus === 'partial';
    const updateFilter = {
      _id: booking._id,
      user: userId,
      paymentStatus: { $ne: 'paid' },
      status: isPartial ? { $in: ['pending', 'confirmed'] } : 'pending',
      ...(isPartial ? {} : { totalPrice: booking.totalPrice })
    };
    if (!isPartial) {
      updateFilter.$or = [{ holdExpiresAt: { $gt: paidAt } }, { holdExpiresAt: { $exists: false } }, { holdExpiresAt: null }];
    }

    const confirmed = await Booking.findOneAndUpdate(
      updateFilter,
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
      payment.active = undefined;
      payment.failureReason = 'La reserva venció o cambió mientras se pagaba';
      await payment.save();
      const fresh = await Booking.findById(booking._id);
      if (fresh && fresh.status === 'pending' && fresh.totalPrice !== booking.totalPrice) {
        throw new PaymentError(409, 'El total de la reserva cambió mientras pagabas. No se cobró; revisa el nuevo total y paga de nuevo.', 'PRICE_CHANGED');
      }
      throw new PaymentError(409, 'La reserva venció mientras pagabas. No se confirmó ni se cobró.', 'HOLD_EXPIRED');
    }

    payment.status = 'approved';
    payment.providerRef = result.providerRef;
    payment.approvedAt = paidAt;
    payment.receiptNumber = receiptFor(payment, paidAt);
    payment.active = undefined;
    await payment.save();

    Promise.resolve(notify({ booking: confirmed, payment })).catch((err) =>
      console.error('[Pagos] Error enviando el comprobante:', err.message));

    return { payment, booking: confirmed, replay: false };
  }
});
