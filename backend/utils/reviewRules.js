import { toCalendarDate } from './dates.js';

export const MAX_COMMENT = 600;

/**
 * ¿Puede el huésped reseñar esta reserva? Solo estadías ya terminadas y pagadas.
 * Devuelve { ok, reason }.
 */
export const reviewEligibility = (booking, today = toCalendarDate(new Date())) => {
  if (!booking) return { ok: false, reason: 'Reserva no encontrada' };
  if (!['confirmed', 'completed'].includes(booking.status)) {
    return { ok: false, reason: 'Solo se reseñan estadías confirmadas' };
  }
  if (!(booking.paymentStatus === 'paid' || booking.amountPaid > 0)) {
    return { ok: false, reason: 'La reserva no tiene un pago registrado' };
  }
  if (new Date(booking.checkOut) > today) {
    return { ok: false, reason: 'Podrás dejar tu reseña cuando termine tu estadía' };
  }
  return { ok: true };
};

/** Nombre público de quien reseña: "Ana P." (nunca el correo ni el nombre completo). */
export const publicName = (name = '') => {
  const [first, ...rest] = String(name).trim().split(/\s+/);
  if (!first) return 'Huésped';
  const last = rest[rest.length - 1];
  return last ? `${first} ${last[0].toUpperCase()}.` : first;
};
