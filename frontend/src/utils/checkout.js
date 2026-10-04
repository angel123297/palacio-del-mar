// Utilidades puras del centro de pago (se prueban con `npm test`).

export const SEASON_NAMES = { low: 'temporada baja', mid: 'temporada media', high: 'temporada alta', peak: 'temporada pico' };

/** Agrupa las noches guardadas en la reserva en líneas "precio × noches · temporada". */
export const groupNights = (nights = []) => {
  const lines = [];
  for (const n of nights) {
    const last = lines[lines.length - 1];
    if (last && last.season === n.season && last.unitPrice === n.price) {
      last.nights += 1;
      last.amount += n.price;
    } else {
      lines.push({ season: n.season, unitPrice: n.price, nights: 1, amount: n.price });
    }
  }
  return lines;
};

/** Texto de la política de cancelación a partir de /api/payments/config. */
export const policyText = (policy) =>
  policy
    ? `Cancelación gratuita hasta ${policy.freeDays} días antes del check-in. Con menos de ${policy.freeDays} días se retiene el ${policy.feePercent} % del total.`
    : '';

export const METHOD_NOTES = {
  card: 'Tarjeta de prueba 4242 4242 4242 4242 (ilustrativa: no se pide ni se envía ningún dato de tarjeta).',
  pse: 'Simulación: se aprueba sin salir del sitio.',
  nequi: 'Simulación: se aprueba sin salir del sitio.'
};

/** Clave del intento de pago: la misma clave nunca cobra dos veces. */
export const newIdempotencyKey = () =>
  String(globalThis.crypto?.randomUUID?.() || `k-${Date.now()}-${Math.random().toString(36).slice(2)}`)
    .replace(/[^A-Za-z0-9_-]/g, '-');
