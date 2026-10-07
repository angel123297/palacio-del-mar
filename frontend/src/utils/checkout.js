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

/**
 * Cuánto lleva pagado el huésped (neto) y cuánto falta, con la MISMA regla que usa el
 * servidor al cobrar (backend/services/paymentService.js), para que el botón "Pagar $X"
 * muestre exactamente lo que se va a cobrar.
 *  - amountPaid es el dinero recibido en bruto; amountRefunded lo ya devuelto.
 *    Neto en poder del hotel = amountPaid - amountRefunded.
 *  - Solo una reserva con pago 'partial' tiene algo pagado; pending/failed = 0.
 *  - Reservas antiguas confirmadas sin amountPaid: se usa el total anterior al último cambio.
 */
export const paymentBalance = (booking) => {
  const total = Math.max(0, Number(booking?.totalPrice) || 0);
  if (booking?.paymentStatus !== 'partial') return { alreadyPaid: 0, remaining: total };

  const gross = Number(booking.amountPaid) || 0;
  const refunded = Number(booking.amountRefunded) || 0;
  let alreadyPaid = gross > 0 ? Math.max(0, gross - refunded) : 0;
  if (gross <= 0 && booking.status === 'confirmed' && booking.modificationHistory?.length) {
    alreadyPaid = Number(booking.modificationHistory[booking.modificationHistory.length - 1].oldTotalPrice) || 0;
  }
  return { alreadyPaid, remaining: Math.max(0, total - alreadyPaid) };
};

/**
 * Total que el huésped tiene pagado (neto de devoluciones) en una reserva ya pagada.
 * amountPaid es bruto: con devoluciones incluiría dinero que ya volvió al huésped.
 * Sin amountPaid (reservas antiguas) se asume pagada por su total.
 */
export const netPaid = (booking) => {
  const gross = Number(booking?.amountPaid) || 0;
  if (gross <= 0) return Math.max(0, Number(booking?.totalPrice) || 0);
  return Math.max(0, gross - (Number(booking?.amountRefunded) || 0));
};

/** Clave del intento de pago: la misma clave nunca cobra dos veces. */
export const newIdempotencyKey = () =>
  String(globalThis.crypto?.randomUUID?.() || `k-${Date.now()}-${Math.random().toString(36).slice(2)}`)
    .replace(/[^A-Za-z0-9_-]/g, '-');
