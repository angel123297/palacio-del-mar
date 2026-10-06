/**
 * Reservas pendientes creadas antes de existir la retención no tienen hora
 * de vencimiento. Se les da una retención completa a partir de ahora (no
 * se vencen de golpe) y desde ahí siguen la regla normal. Idempotente.
 */
import Booking from '../models/Booking.js';
import { holdDeadline } from '../utils/bookingRules.js';

export const runHoldMigration = async () => {
  const res = await Booking.collection.updateMany(
    { status: 'pending', $or: [{ holdExpiresAt: { $exists: false } }, { holdExpiresAt: null }] },
    { $set: { holdExpiresAt: holdDeadline() } }
  );
  if (res.modifiedCount) {
    console.log(`[Migración] ${res.modifiedCount} reserva(s) pendiente(s) antigua(s) recibieron hora de vencimiento.`);
  }
  return res.modifiedCount;
};
