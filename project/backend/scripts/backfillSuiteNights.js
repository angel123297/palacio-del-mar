/**
 * Reconcilia los bloqueos de noches (SuiteNight) con las reservas.
 *
 * Ejecutar UNA VEZ al desplegar la protección contra doble reserva
 * (las reservas creadas antes no tienen bloqueos) y, si se desea, de forma
 * periódica:
 *
 *   npm run backfill-nights            # solo informa (no modifica nada)
 *   npm run backfill-nights -- --apply # crea bloqueos faltantes y limpia huérfanos
 *
 * - Crea los bloqueos de todas las reservas pending/confirmed.
 * - Si dos reservas activas YA se solapan (doble reserva histórica), la
 *   segunda no puede bloquear: se lista para resolverla manualmente.
 * - Elimina bloqueos huérfanos (reserva inexistente o ya no bloqueante).
 */
import dotenv from 'dotenv';
import connectDB, { closeConnection } from '../database/db.js';
import Booking, { BLOCKING_BOOKING_STATUSES } from '../models/Booking.js';
import SuiteNight, { NightsConflictError } from '../models/SuiteNight.js';
import { eachNight } from '../utils/dates.js';

dotenv.config();
const apply = process.argv.includes('--apply');

const run = async () => {
  await connectDB();
  await SuiteNight.init();
  console.log(apply ? '▶ Modo --apply (modifica datos)' : '▶ Modo simulación (usa --apply para modificar)');

  const conflicts = [];
  let created = 0;

  const bookings = await Booking.find({ status: { $in: BLOCKING_BOOKING_STATUSES } })
    .sort('createdAt')
    .select('suite checkIn checkOut status createdAt');

  for (const b of bookings) {
    const held = new Set((await SuiteNight.find({ booking: b._id }).select('date').lean()).map((n) => n.date.getTime()));
    const missing = eachNight(b.checkIn, b.checkOut).filter((d) => !held.has(d.getTime()));
    if (!missing.length) continue;
    if (!apply) { created += missing.length; continue; }
    try {
      await SuiteNight.acquire(b.suite, b._id, missing);
      created += missing.length;
    } catch (err) {
      if (err instanceof NightsConflictError) conflicts.push(String(b._id));
      else throw err;
    }
  }

  // Huérfanos: bloqueos cuya reserva no existe o ya no bloquea noches
  const activeIds = new Set(bookings.map((b) => String(b._id)));
  const bookingIdsWithLocks = await SuiteNight.distinct('booking');
  const orphanIds = bookingIdsWithLocks.filter((id) => !activeIds.has(String(id)));
  let orphans = 0;
  for (const id of orphanIds) {
    orphans += await SuiteNight.countDocuments({ booking: id });
    if (apply) await SuiteNight.release(id);
  }

  console.log(`Bloqueos ${apply ? 'creados' : 'por crear'}: ${created}`);
  console.log(`Bloqueos huérfanos ${apply ? 'eliminados' : 'detectados'}: ${orphans}`);
  if (conflicts.length) {
    console.log(`⚠️ Reservas activas que se solapan con otra (resolver manualmente): ${conflicts.length}`);
    conflicts.forEach((id) => console.log('   -', id));
  }

  await closeConnection();
  process.exit(0);
};

run().catch((e) => {
  console.error('❌ Error:', e.message);
  process.exit(1);
});
