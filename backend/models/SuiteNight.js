import mongoose from 'mongoose';

// ============================================
// BLOQUEO ATÓMICO DE NOCHES (BUG-001)
// ============================================
// Cada noche ocupada de una suite es un documento con clave ÚNICA
// (suite, date). Reservar = insertar esos documentos; si otra solicitud ya
// tiene alguna de las noches, MongoDB rechaza el insert (E11000) y la
// reserva se rechaza. Funciona en MongoDB standalone (sin réplica ni
// transacciones), a diferencia de una transacción con findOne + insert.

export class NightsConflictError extends Error {
  constructor() {
    super('La suite no está disponible para las fechas seleccionadas');
    this.name = 'NightsConflictError';
    this.code = 'NIGHTS_CONFLICT';
  }
}

const suiteNightSchema = new mongoose.Schema(
  {
    suite: { type: mongoose.Schema.Types.ObjectId, ref: 'Suite', required: true },
    date: { type: Date, required: true }, // medianoche UTC de la noche
    booking: { type: mongoose.Schema.Types.ObjectId, ref: 'Booking', required: true, index: true }
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

suiteNightSchema.index({ suite: 1, date: 1 }, { unique: true });

/**
 * Adquiere las noches indicadas para una reserva. Todo o nada: si alguna
 * está tomada, libera las que sí logró insertar y lanza NightsConflictError.
 * Devuelve las fechas adquiridas (para poder revertir después).
 */
suiteNightSchema.statics.acquire = async function (suiteId, bookingId, nights) {
  if (!nights.length) return [];

  const results = await Promise.allSettled(
    nights.map((date) => this.create({ suite: suiteId, booking: bookingId, date }))
  );

  const acquired = [];
  let conflict = false;
  let otherError = null;
  results.forEach((r, i) => {
    if (r.status === 'fulfilled') acquired.push(nights[i]);
    else if (r.reason?.code === 11000) conflict = true;
    else otherError = r.reason;
  });

  if (conflict || otherError) {
    if (acquired.length) {
      await this.deleteMany({ suite: suiteId, booking: bookingId, date: { $in: acquired } });
    }
    if (otherError) throw otherError;
    throw new NightsConflictError();
  }
  return acquired;
};

/** Libera todas las noches de una reserva, o solo las fechas indicadas. */
suiteNightSchema.statics.release = async function (bookingId, dates = null) {
  const filter = { booking: bookingId };
  if (dates) filter.date = { $in: dates };
  return this.deleteMany(filter);
};

const SuiteNight = mongoose.models.SuiteNight || mongoose.model('SuiteNight', suiteNightSchema);
export default SuiteNight;
