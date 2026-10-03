import mongoose from 'mongoose';

// ============================================
// BLOQUEO ATÓMICO DE NOCHES POR HABITACIÓN FÍSICA
// ============================================
// Una Suite es un TIPO de habitación de una sucursal y tiene N habitaciones
// físicas (totalUnits). Cada habitación física es un "slot" 1..N.
// Cada noche ocupada de un slot es un documento con clave ÚNICA
// (suite, slot, date). Reservar = insertar esos documentos; si otra solicitud
// ya tiene alguna de las noches de ESE slot, MongoDB rechaza el insert
// (E11000). Para reservar "cualquier habitación libre" se prueba un slot
// tras otro. Funciona en MongoDB standalone (sin réplica ni transacciones).

export class NightsConflictError extends Error {
  constructor(message = 'La suite no está disponible para las fechas seleccionadas') {
    super(message);
    this.name = 'NightsConflictError';
    this.code = 'NIGHTS_CONFLICT';
  }
}

const suiteNightSchema = new mongoose.Schema(
  {
    suite: { type: mongoose.Schema.Types.ObjectId, ref: 'Suite', required: true },
    slot: { type: Number, required: true, min: 1, default: 1 }, // habitación física (1..totalUnits)
    date: { type: Date, required: true }, // medianoche UTC de la noche
    booking: { type: mongoose.Schema.Types.ObjectId, ref: 'Booking', required: true, index: true }
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

suiteNightSchema.index({ suite: 1, slot: 1, date: 1 }, { unique: true });

/**
 * Adquiere las noches indicadas en UN slot concreto. Todo o nada: si alguna
 * está tomada, libera las que sí logró insertar y lanza NightsConflictError.
 * Devuelve las fechas adquiridas (para poder revertir después).
 */
suiteNightSchema.statics.acquire = async function (suiteId, bookingId, nights, slot = 1) {
  if (!nights.length) return [];

  // Las noches se insertan EN ORDEN y de una en una. La primera noche actúa
  // como puerta de entrada: quien la gana avanza, el resto falla de inmediato
  // sin dejar noches sueltas. (Insertarlas todas en paralelo hacía que varias
  // solicitudes se quedaran con noches distintas, chocaran entre sí y todas
  // hicieran rollback: nadie ganaba.)
  const sorted = [...nights].sort((a, b) => a - b);
  const acquired = [];
  try {
    for (const date of sorted) {
      await this.create({ suite: suiteId, slot, booking: bookingId, date });
      acquired.push(date);
    }
    return acquired;
  } catch (err) {
    // Rollback solo de lo propio (misma reserva y mismo slot)
    if (acquired.length) {
      await this.deleteMany({ suite: suiteId, slot, booking: bookingId, date: { $in: acquired } });
    }
    if (err?.code === 11000) throw new NightsConflictError();
    throw err;
  }
};

/**
 * Adquiere las noches en la primera habitación física libre (slot 1..capacity).
 * Devuelve { slot, nights }. Si todas están ocupadas lanza NightsConflictError.
 * Los slots se prueban en orden: es determinista y deja las últimas habitaciones
 * libres para quien las pida después. Si todos fallan, reintenta unas rondas
 * con una espera corta: otra solicitud pudo tener un slot por un instante y
 * liberarlo en su rollback.
 */
const ACQUIRE_ROUNDS = 3;
suiteNightSchema.statics.acquireAny = async function (suiteId, bookingId, nights, capacity = 1) {
  const total = Math.max(1, Math.floor(Number(capacity)) || 1);
  for (let round = 0; round < ACQUIRE_ROUNDS; round++) {
    for (let slot = 1; slot <= total; slot++) {
      try {
        const acquired = await this.acquire(suiteId, bookingId, nights, slot);
        return { slot, nights: acquired };
      } catch (err) {
        if (!(err instanceof NightsConflictError)) throw err;
        // ese slot está ocupado en alguna noche: se prueba el siguiente
      }
    }
    if (round < ACQUIRE_ROUNDS - 1) {
      await new Promise((r) => setTimeout(r, 5 + Math.random() * 25));
    }
  }
  throw new NightsConflictError();
};

/**
 * Habitaciones físicas libres de una suite en las noches indicadas.
 * Es una consulta informativa (no reserva nada): la garantía es acquire().
 */
suiteNightSchema.statics.freeSlots = async function (suiteId, nights, capacity = 1) {
  const total = Math.max(0, Math.floor(Number(capacity)) || 0);
  if (!total || !nights.length) return Array.from({ length: total }, (_, i) => i + 1);
  const taken = new Set(await this.distinct('slot', { suite: suiteId, date: { $in: nights } }));
  const free = [];
  for (let slot = 1; slot <= total; slot++) if (!taken.has(slot)) free.push(slot);
  return free;
};

/** Libera todas las noches de una reserva, o solo las fechas indicadas. */
suiteNightSchema.statics.release = async function (bookingId, dates = null) {
  const filter = { booking: bookingId };
  if (dates) filter.date = { $in: dates };
  return this.deleteMany(filter);
};

const SuiteNight = mongoose.models.SuiteNight || mongoose.model('SuiteNight', suiteNightSchema);
export default SuiteNight;
