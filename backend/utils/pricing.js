import { eachNight } from './dates.js';
import { getSeason, calculateSeasonalPrice } from './seasons.js';

// ============================================
// CANCELACIÓN Y REEMBOLSO (BUG-003)
// ============================================
// Sin dependencias, para poder probarlo aislado. El reembolso se calcula
// sobre lo EFECTIVAMENTE COBRADO, nunca sobre el precio de la reserva.

/** Política de cancelación (única fuente; el sitio la lee de /api/payments/config). */
export const CANCELLATION_POLICY = { freeDays: 7, feePercent: 10 };

const round2 = (n) => Math.round((Number(n) || 0) * 100) / 100;

/**
 * Importe neto que el hotel tiene cobrado (cobrado - ya reembolsado).
 * Compatibilidad: reservas marcadas 'paid' antes de existir `amountPaid`
 * (amountPaid = 0) se consideran cobradas por su total.
 */
export const getCollectedAmount = (booking) => {
  const { paymentStatus, totalPrice = 0 } = booking;
  const amountPaid = Number(booking.amountPaid) || 0;
  const amountRefunded = Number(booking.amountRefunded) || 0;

  let gross = 0;
  if (paymentStatus === 'paid') gross = amountPaid > 0 ? amountPaid : totalPrice;
  else if (paymentStatus === 'partial') gross = amountPaid;
  else return 0; // pending, failed y refunded: no hay dinero por devolver

  return Math.max(0, round2(gross - amountRefunded));
};

/**
 * Penalización (10% del precio si faltan menos de 7 días) y reembolso.
 * refund = max(0, cobrado_neto - penalización_retenida); la penalización
 * retenida nunca supera lo cobrado.
 */
export const calculateCancellation = (booking, daysUntilCheckIn) => {
  const collected = getCollectedAmount(booking);
  const policyFee =
    daysUntilCheckIn >= 0 && daysUntilCheckIn < CANCELLATION_POLICY.freeDays
      ? round2((booking.totalPrice || 0) * (CANCELLATION_POLICY.feePercent / 100))
      : 0;
  const cancellationFee = Math.min(policyFee, collected);
  const refundAmount = Math.max(0, round2(collected - cancellationFee));

  return {
    collected,
    cancellationFee,
    refundAmount,
    // 'none' = no hay nada que devolver; 'pending' = falta ejecutar la devolución
    refundStatus: refundAmount > 0 ? 'pending' : 'none'
  };
};

// ============================================
// PRECIOS DE UNA ESTADÍA (única fuente)
// ============================================
// Reglas:
//  1. Cada NOCHE se cobra con el recargo de SU temporada (antes se usaba la
//     de la noche de entrada para toda la estadía).
//  2. Una promoción de la sucursal descuenta su % de cada noche que cubre.
//  3. Estadía larga: 5% desde 5 noches, 10% desde 7, sobre alojamiento + experiencias.
//  4. NO se acumulan: se aplica el descuento que más ahorra al huésped.
// Funciones puras (sin base de datos): reciben las promociones ya cargadas.

export const LONG_STAY_RULES = [
  { minNights: 7, percent: 10 },
  { minNights: 5, percent: 5 }
];

export const longStayPercent = (nights) =>
  LONG_STAY_RULES.find((r) => nights >= r.minNights)?.percent || 0;

const dayIso = (d) => new Date(d).toISOString().slice(0, 10);

/** Mejor promoción (mayor %) que cubre una noche, o null. */
const promotionFor = (night, promotions) => {
  const day = dayIso(night);
  let best = null;
  for (const p of promotions || []) {
    if (p.active === false) continue;
    if (day >= dayIso(p.startDate) && day <= dayIso(p.endDate)) {
      if (!best || p.discountPercent > best.discountPercent) best = p;
    }
  }
  return best;
};

/** Una fila por noche: temporada, precio y promoción aplicada. */
export const buildNights = ({ basePrice, checkIn, checkOut, promotions = [] }) =>
  eachNight(checkIn, checkOut).map((date) => {
    const season = getSeason(date);
    const promo = promotionFor(date, promotions);
    return {
      date,
      season,
      price: calculateSeasonalPrice(basePrice, season),
      promoPercent: promo ? promo.discountPercent : 0,
      promotionId: promo?._id,
      promotionTitle: promo?.title
    };
  });

/** Totales a partir del alojamiento ya calculado. Núcleo común a todo. */
export const computeTotals = ({ lodging, promoDiscount = 0, promoLabel = null, nightsCount, experiencesTotal = 0 }) => {
  const gross = lodging + experiencesTotal;
  const longPercent = longStayPercent(nightsCount);
  const longDiscount = Math.round((gross * longPercent) / 100);

  let discount = 0;
  let discountType = null;
  let discountReason = null;
  if (promoDiscount > 0 && promoDiscount >= longDiscount) {
    discount = promoDiscount;
    discountType = 'promotion';
    discountReason = promoLabel;
  } else if (longDiscount > 0) {
    discount = longDiscount;
    discountType = 'long_stay';
    const rule = LONG_STAY_RULES.find((r) => r.percent === longPercent);
    discountReason = `Descuento por estadía de ${rule.minNights}+ noches (${longPercent}%)`;
  }

  return {
    lodging,
    experiencesTotal,
    discount,
    discountType,
    discountReason,
    total: Math.max(0, Math.round(gross - discount)),
    nightlyAverage: nightsCount ? Math.round(lodging / nightsCount) : 0
  };
};

const promoSummary = (nights) => {
  const covered = nights.filter((n) => n.promoPercent > 0);
  if (!covered.length) return { promoDiscount: 0, promoLabel: null };
  const promoDiscount = covered.reduce((sum, n) => sum + Math.round((n.price * n.promoPercent) / 100), 0);
  const titles = [...new Set(covered.map((n) => n.promotionTitle).filter(Boolean))];
  const percents = [...new Set(covered.map((n) => n.promoPercent))];
  const pct = percents.length === 1 ? `${percents[0]}%` : `hasta ${Math.max(...percents)}%`;
  return {
    promoDiscount,
    promoLabel: `Promoción${titles.length ? ` "${titles.join('" + "')}"` : ''} (-${pct} en ${covered.length} de ${nights.length} noches)`
  };
};

/** Totales de una lista de noches (la que se guarda en la reserva). */
export const totalsFromNights = (nights, experiencesTotal = 0) => {
  const lodging = nights.reduce((sum, n) => sum + n.price, 0);
  return computeTotals({ lodging, ...promoSummary(nights), nightsCount: nights.length, experiencesTotal });
};

/** Cotización completa de una estadía. */
export const quoteStay = ({ basePrice, checkIn, checkOut, experiencesTotal = 0, promotions = [] }) => {
  const nights = buildNights({ basePrice, checkIn, checkOut, promotions });
  return { nights, ...totalsFromNights(nights, experiencesTotal) };
};

/**
 * Líneas legibles del desglose: noches consecutivas con el mismo precio y
 * temporada se agrupan ("3 noches × $860.000").
 */
export const groupNights = (nights) => {
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

/** Resumen público de una cotización (lo que muestra el detalle de la habitación). */
export const summarizeQuote = (quote, lines) => ({
  nights: quote.nights.length,
  lines,
  lodging: quote.lodging,
  discount: quote.discount,
  discountReason: quote.discountReason,
  totalPrice: quote.total
});
