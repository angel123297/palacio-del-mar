// Reglas de retención de reservas pendientes. Valores por defecto locales;
// se pueden cambiar con variables del docker-compose, nunca con un .env obligatorio.
const intFromEnv = (name, fallback, { min = 1, max = 100000 } = {}) => {
  const n = Number.parseInt(process.env[name], 10);
  return Number.isFinite(n) && n >= min && n <= max ? n : fallback;
};

/** Minutos que se retienen las noches de una reserva pendiente de pago. */
export const holdMinutes = () => intFromEnv('HOLD_MINUTES', 30, { min: 1, max: 1440 });

/** Máximo de reservas pendientes (vigentes) por usuario. */
export const maxPendingPerUser = () => intFromEnv('MAX_PENDING_BOOKINGS', 3, { min: 1, max: 20 });

/** Cada cuánto se buscan reservas vencidas (segundos). */
export const expirySweepSeconds = () => intFromEnv('EXPIRY_SWEEP_SECONDS', 60, { min: 5, max: 3600 });

export const holdDeadline = (from = new Date()) => new Date(from.getTime() + holdMinutes() * 60000);
