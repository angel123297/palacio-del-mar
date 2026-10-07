/**
 * Repara reservas dañadas por un bug anterior: al cambiar las fechas de una reserva
 * PENDIENTE (nunca pagada) a un rango más caro, quedaba con paymentStatus='partial'
 * sin dinero cobrado. Eso tenía dos efectos: se cobraba solo la diferencia al pagar y
 * el barrido de vencimiento (solo mira pending/failed) nunca la liberaba.
 *
 * Criterio de reparación (conservador): status='pending', paymentStatus='partial',
 * amountPaid en 0/ausente y SIN ningún pago aprobado en la colección de pagos.
 * Se devuelven a paymentStatus='pending'. Idempotente: una segunda ejecución no
 * encuentra nada. No toca reservas confirmadas ni con dinero recibido.
 *
 * `Booking` y `Payment` se pueden inyectar para probarla sin base de datos.
 */
import BookingModel from '../models/Booking.js';
import PaymentModel from '../models/Payment.js';

export const runRepairPartialPending = async ({ Booking = BookingModel, Payment = PaymentModel } = {}) => {
  const candidates = await Booking.collection
    .find({
      status: 'pending',
      paymentStatus: 'partial',
      $or: [{ amountPaid: { $exists: false } }, { amountPaid: null }, { amountPaid: 0 }]
    }, { projection: { _id: 1 } })
    .toArray();
  if (!candidates.length) return 0;

  const ids = candidates.map((c) => c._id);
  // Si hay un pago aprobado, sí entró dinero: esa reserva no se toca
  const withMoney = new Set((await Payment.distinct('booking', { booking: { $in: ids }, status: 'approved' })).map(String));
  const toFix = ids.filter((id) => !withMoney.has(String(id)));
  if (!toFix.length) return 0;

  const res = await Booking.collection.updateMany(
    { _id: { $in: toFix }, status: 'pending', paymentStatus: 'partial' },
    { $set: { paymentStatus: 'pending' } }
  );
  if (res.modifiedCount) {
    console.log(`[Migración] ${res.modifiedCount} reserva(s) pendiente(s) sin pago volvieron a estado de pago "pending" (bug de cambio de fechas).`);
  }
  return res.modifiedCount;
};
