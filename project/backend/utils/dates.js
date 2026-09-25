// ============================================
// FECHAS DE CALENDARIO (CONS-014)
// ============================================
// Una noche de hotel es una FECHA DE CALENDARIO, no un instante. Toda fecha
// de check-in/check-out se guarda como medianoche UTC de ese día del
// calendario (independiente de la zona horaria del servidor) y las noches se
// cuentan con diferencias exactas de días. "Hoy" se calcula en la zona
// horaria del hotel (HOTEL_TIMEZONE, por defecto America/Bogota).

export const HOTEL_TIMEZONE = process.env.HOTEL_TIMEZONE || 'America/Bogota';
const DAY_MS = 24 * 60 * 60 * 1000;

const ymdFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: HOTEL_TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit'
});

const utcMidnight = (y, m, d) => {
  const date = new Date(Date.UTC(y, m - 1, d));
  // Rechaza fechas imposibles como 2026-02-31
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) return null;
  return date;
};

const isUtcMidnight = (d) =>
  d.getUTCHours() === 0 && d.getUTCMinutes() === 0 && d.getUTCSeconds() === 0 && d.getUTCMilliseconds() === 0;

const instantToHotelDate = (instant) => {
  const [y, m, d] = ymdFormatter.format(instant).split('-').map(Number);
  return utcMidnight(y, m, d);
};

/**
 * Convierte "YYYY-MM-DD", un Date o un ISO string en la fecha de calendario
 * (medianoche UTC). Devuelve null si no es una fecha válida.
 *  - "YYYY-MM-DD"                 -> ese día tal cual.
 *  - Date a medianoche UTC exacta -> ya es una fecha de calendario (viene de la BD).
 *  - Cualquier otro instante      -> el día que es en la zona del hotel.
 */
export const toCalendarDate = (input) => {
  if (input === null || input === undefined || input === '') return null;

  if (typeof input === 'string') {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(input.trim());
    if (m) return utcMidnight(Number(m[1]), Number(m[2]), Number(m[3]));
  }

  const date = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(date.getTime())) return null;
  return isUtcMidnight(date) ? new Date(date.getTime()) : instantToHotelDate(date);
};

/** Fecha de calendario de "hoy" en la zona horaria del hotel. */
export const todayCalendarDate = (now = new Date()) => instantToHotelDate(now);

/** Número exacto de noches entre dos fechas de calendario. */
export const calculateNights = (checkIn, checkOut) =>
  Math.round((toCalendarDate(checkOut) - toCalendarDate(checkIn)) / DAY_MS);

/** Lista de noches ocupadas: check-in incluido, check-out excluido. */
export const eachNight = (checkIn, checkOut) => {
  const start = toCalendarDate(checkIn).getTime();
  const nights = calculateNights(checkIn, checkOut);
  return Array.from({ length: Math.max(0, nights) }, (_, i) => new Date(start + i * DAY_MS));
};

/** Días completos desde `from` hasta `to` (ambas fechas de calendario). */
export const daysBetween = (from, to) =>
  Math.round((toCalendarDate(to) - toCalendarDate(from)) / DAY_MS);
