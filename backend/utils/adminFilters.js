// Reglas puras del panel de administración (sin base de datos, fáciles de probar).
// Las fechas son FECHAS DE CALENDARIO (utils/dates.js): medianoche UTC.
import { toCalendarDate } from './dates.js';

const DAY_MS = 24 * 60 * 60 * 1000;

export const BOOKING_VIEWS = ['all', 'arrivals', 'departures', 'inhouse', 'upcoming', 'pending', 'cancelled', 'expired'];

const nextDay = (day) => new Date(day.getTime() + DAY_MS);

/**
 * Filtro de Mongo para cada "vista" de reservas del panel (como las pestañas de
 * Reservas de Booking.com: llegadas, salidas, en el hotel...). `today` es la
 * fecha de calendario de hoy en la zona horaria del hotel.
 */
export const bookingViewFilter = (view, today) => {
  switch (view) {
    case 'arrivals':
      return { status: { $in: ['pending', 'confirmed'] }, checkIn: { $gte: today, $lt: nextDay(today) } };
    case 'departures':
      return { status: { $in: ['confirmed', 'completed'] }, checkOut: { $gte: today, $lt: nextDay(today) } };
    case 'inhouse':
      return { status: 'confirmed', checkIn: { $lte: today }, checkOut: { $gt: today } };
    case 'upcoming':
      return { status: { $in: ['pending', 'confirmed'] }, checkIn: { $gte: nextDay(today) } };
    case 'pending':
      return { status: 'pending' };
    case 'cancelled':
      return { status: { $in: ['cancelled', 'no_show'] } };
    case 'expired':
      return { status: 'expired' };
    default:
      return {};
  }
};

/** Orden recomendado de cada vista. */
export const bookingViewSort = (view) =>
  view === 'arrivals' || view === 'upcoming' ? { checkIn: 1 }
    : view === 'departures' || view === 'inhouse' ? { checkOut: 1 }
      : { createdAt: -1 };

/** Escapa texto del usuario para usarlo dentro de una expresión regular. */
export const escapeRegex = (text) => String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Estado de una promoción respecto a hoy: paused | past | upcoming | current. */
export const promotionState = (promo, today) => {
  if (!promo.active) return 'paused';
  const start = toCalendarDate(promo.startDate);
  const end = toCalendarDate(promo.endDate);
  if (end < today) return 'past';
  if (start > today) return 'upcoming';
  return 'current';
};

const MAX_PROMO_NIGHTS = 366;

/**
 * Valida los datos de una promoción. `existing` (opcional) son los valores
 * actuales: al editar solo hace falta mandar lo que cambia.
 * Devuelve { errors: [...], data: {...valores normalizados} }.
 */
export const validatePromotionInput = (body = {}, existing = null) => {
  const errors = [];
  const data = {};
  const has = (key) => body[key] !== undefined;

  const title = has('title') ? body.title : existing?.title;
  if (typeof title !== 'string' || title.trim().length < 3 || title.trim().length > 80) {
    errors.push('El título debe tener entre 3 y 80 caracteres');
  } else {
    data.title = title.trim();
  }

  const percent = has('discountPercent') ? Number(body.discountPercent) : existing?.discountPercent;
  if (!Number.isInteger(percent) || percent < 1 || percent > 60) {
    errors.push('El descuento debe ser un número entero entre 1 y 60');
  } else {
    data.discountPercent = percent;
  }

  const start = toCalendarDate(has('startDate') ? body.startDate : existing?.startDate);
  const end = toCalendarDate(has('endDate') ? body.endDate : existing?.endDate);
  if (!start) errors.push('La primera noche no es una fecha válida (use YYYY-MM-DD)');
  if (!end) errors.push('La última noche no es una fecha válida (use YYYY-MM-DD)');
  if (start && end) {
    if (end < start) {
      errors.push('La última noche no puede ser anterior a la primera');
    } else if ((end - start) / DAY_MS + 1 > MAX_PROMO_NIGHTS) {
      errors.push(`Una promoción no puede cubrir más de ${MAX_PROMO_NIGHTS} noches`);
    } else {
      data.startDate = start;
      data.endDate = end;
    }
  }

  if (has('active')) {
    if (typeof body.active !== 'boolean') errors.push('"active" debe ser verdadero o falso');
    else data.active = body.active;
  }

  return { errors, data };
};
