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
import Suite from '../models/Suite.js';
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
    .select('suite checkIn checkOut status createdAt unitSlot');
  const capacityBySuite = new Map();
  const capacityOf = async (suiteId) => {
    const key = String(suiteId);
    if (!capacityBySuite.has(key)) {
      const suite = await Suite.findById(suiteId).select('totalUnits units available');
      capacityBySuite.set(key, suite ? Math.max(1, suite.availableUnitsCount) : 1);
    }
    return capacityBySuite.get(key);
  };

  for (const b of bookings) {
    const heldNights = await SuiteNight.find({ booking: b._id }).select('date slot').lean();
    const held = new Set(heldNights.map((n) => n.date.getTime()));
    const missing = eachNight(b.checkIn, b.checkOut).filter((d) => !held.has(d.getTime()));
    if (!missing.length) continue;
    if (!apply) { created += missing.length; continue; }
    try {
      if (heldNights.length) {
        // ya tiene habitación asignada: se completan las noches en la misma
        await SuiteNight.acquire(b.suite, b._id, missing, heldNights[0].slot);
      } else {
        const { slot } = await SuiteNight.acquireAny(b.suite, b._id, missing, await capacityOf(b.suite));
        await Booking.updateOne({ _id: b._id }, { $set: { unitSlot: slot } });
      }
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
