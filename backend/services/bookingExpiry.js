// Vencimiento de reservas pendientes: sin esto una reserva pendiente
// retenía las noches para siempre.
import Booking from '../models/Booking.js';
import SuiteNight from '../models/SuiteNight.js';
import '../models/Suite.js'; // este servicio hace populate('suite'): registra el modelo aunque se use aislado
import { sendBookingExpiredEmail } from '../utils/email.js';
import { expirySweepSeconds } from '../utils/bookingRules.js';

/**
 * Vence las reservas pendientes cuya retención terminó. Cada una se "reclama"
 * con una actualización atómica condicionada al estado: si el huésped paga
 * (o un admin confirma) justo antes, la reserva ya no es "pending" y no se
 * toca; si dos procesos barren a la vez, solo uno la reclama.
 * Solo vencen reservas SIN dinero recibido (paymentStatus pending/failed).
 * Devuelve cuántas venció.
 */
export const expireStaleBookings = async (now = new Date()) => {
  const stale = await Booking.find({
    status: 'pending',
    paymentStatus: { $in: ['pending', 'failed'] },
    holdExpiresAt: { $lte: now }
  }).select('_id').limit(200).lean();

  let expired = 0;
  for (const { _id } of stale) {
    const claimed = await Booking.findOneAndUpdate(
      { _id, status: 'pending', paymentStatus: { $in: ['pending', 'failed'] }, holdExpiresAt: { $lte: now } },
      { $set: { status: 'expired', expiredAt: now } },
      { new: true }
    ).populate('suite', 'name');
    if (!claimed) continue; // otro proceso o un pago llegó primero

    expired += 1;
    try {
      await SuiteNight.release(claimed._id);
    } catch (err) {
      // Fallo seguro: las noches quedan bloqueadas (nunca se sobrevende);
      // scripts/backfillSuiteNights.js --apply las reconcilia.
      console.error(`[Vencimiento] No se liberaron las noches de ${claimed._id}:`, err.message);
    }
    if (claimed.guestEmail) {
      sendBookingExpiredEmail(claimed, claimed.guestEmail, claimed.guestName).catch((err) =>
        console.error('[Vencimiento] Error enviando correo:', err.message));
    }
  }
  return expired;
};

let timer = null;

/** Arranca el barrido periódico (una sola vez por proceso). */
export const startBookingExpiry = () => {
  if (timer) return;
  let running = false;
  const tick = async () => {
    if (running) return;
    running = true;
    try {
      const n = await expireStaleBookings();
      if (n) console.log(`[Vencimiento] ${n} reserva(s) pendiente(s) vencida(s); noches liberadas.`);
    } catch (err) {
      console.error('[Vencimiento] Error en el barrido:', err.message);
    } finally {
      running = false;
    }
  };
  timer = setInterval(tick, expirySweepSeconds() * 1000);
  timer.unref();
  tick();
};

export const stopBookingExpiry = () => {
  if (timer) clearInterval(timer);
  timer = null;
};
