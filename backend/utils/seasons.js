// ============================================
// TEMPORADAS Y FESTIVOS: ÚNICA FUENTE
// ============================================
// Antes había tres: suiteController (precio), availabilityController
// (/peak-dates, escrito a mano) y las promociones. Todo lo que necesite
// saber "qué temporada es esta noche" debe pasar por aquí. Sin dependencias
// ni base de datos, para poder probarlo.
//
// Las fechas son medianoche UTC (ver utils/dates.js).

export const SEASON_MULTIPLIERS = {
  low: 1.0, // temporada baja
  mid: 1.15, // temporada media
  high: 1.3, // temporada alta
  peak: 1.5 // temporada pico (Navidad, Semana Santa)
};

export const SEASON_LABELS = {
  low: 'Temporada baja',
  mid: 'Temporada media',
  high: 'Temporada alta',
  peak: 'Temporada pico'
};

// Rangos que se repiten cada año (MM-DD). Si start > end, cruzan de año.
const PEAK_FIXED = [{ name: 'Navidad y Año Nuevo', start: '12-21', end: '01-10' }];
const HIGH_FIXED = [
  { name: 'Vacaciones de mitad de año', start: '06-15', end: '07-15' },
  { name: 'Pre-navidad', start: '12-15', end: '12-20' }
];
// Mitad de junio y de diciembre que no son temporada alta
const MID_FIXED = [
  { name: 'Junio', start: '06-01', end: '06-14' },
  { name: 'Inicio de diciembre', start: '12-01', end: '12-14' }
];

// Festivos nominales (la fecha del calendario, sin trasladar al lunes)
const HOLIDAYS = [
  ['Año Nuevo', '01-01'],
  ['Día de los Reyes Magos', '01-06'],
  ['Día de San José', '03-19'],
  ['Día del Trabajo', '05-01'],
  ['Día de la Independencia', '07-20'],
  ['Día de la Raza', '10-12'],
  ['Día de Todos los Santos', '11-01'],
  ['Día de la Inmaculada Concepción', '12-08'],
  ['Navidad', '12-25']
];

const iso = (date) => date.toISOString().slice(0, 10);

const inMonthDayRange = (md, { start, end }) =>
  start <= end ? md >= start && md <= end : md >= start || md <= end;

/** Domingo de Pascua (algoritmo de Meeus/Jones/Butcher), como Date UTC. */
export const easterSunday = (year) => {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31); // 3 = marzo, 4 = abril
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(Date.UTC(year, month - 1, day));
};

/** Semana Santa: del Domingo de Ramos al Domingo de Resurrección. */
export const holyWeek = (year) => {
  const end = easterSunday(year);
  const start = new Date(end.getTime() - 7 * 86400000);
  return { name: 'Semana Santa', start: iso(start), end: iso(end) };
};

/** Temporada de una noche (Date UTC a medianoche). */
export const getSeason = (date) => {
  const day = iso(date);
  const md = day.slice(5, 10);
  const year = Number(day.slice(0, 4));

  const week = holyWeek(year);
  if (day >= week.start && day <= week.end) return 'peak';
  if (PEAK_FIXED.some((r) => inMonthDayRange(md, r))) return 'peak';
  if (HIGH_FIXED.some((r) => inMonthDayRange(md, r))) return 'high';
  if (MID_FIXED.some((r) => inMonthDayRange(md, r))) return 'mid';
  return 'low';
};

/** Precio de una noche con el recargo de su temporada (pesos enteros). */
export const calculateSeasonalPrice = (basePrice, season) =>
  Math.round(basePrice * (SEASON_MULTIPLIERS[season] || 1));

/**
 * Calendario de un año en el formato de GET /availability/peak-dates.
 * Navidad empieza el 21/12 y termina el 10/01 del año siguiente; el
 * calendario del frontend pide también el año anterior para cubrir enero.
 */
export const getSeasonCalendar = (year) => ({
  highSeason: HIGH_FIXED.map((r) => ({ name: r.name, start: `${year}-${r.start}`, end: `${year}-${r.end}` })),
  peakSeason: [
    ...PEAK_FIXED.map((r) => ({ name: r.name, start: `${year}-${r.start}`, end: `${year + 1}-${r.end}` })),
    holyWeek(year)
  ],
  holidays: HOLIDAYS.map(([name, md]) => ({ name, date: `${year}-${md}` }))
});
