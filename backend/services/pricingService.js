// Acceso a datos para cotizar: carga suite, experiencias y promociones y
// delega el cálculo en utils/pricing.js (que no toca la base de datos).
import Suite from '../models/Suite.js';
import Experience from '../models/Experience.js';
import Promotion from '../models/Promotion.js';
import { quoteStay, groupNights } from '../utils/pricing.js';
import { getSeason } from '../utils/seasons.js';

/** Promociones activas de las sucursales que tocan alguna noche de [checkIn, checkOut). */
export const loadPromotions = (branchIds, checkIn, checkOut) => {
  const ids = (Array.isArray(branchIds) ? branchIds : [branchIds]).filter(Boolean);
  if (!ids.length) return Promise.resolve([]);
  return Promotion.find({
    branch: { $in: ids },
    active: true,
    startDate: { $lt: checkOut },
    endDate: { $gte: checkIn }
  }).lean();
};

/**
 * Cotiza una estadía en una suite. `suite` puede ser un documento o un id.
 * `promotions` se puede pasar ya cargado (búsquedas con muchas suites).
 */
export const quoteSuiteStay = async ({ suite, checkIn, checkOut, experienceIds = [], onlyAvailable = false, promotions }) => {
  const doc = suite?.basePrice !== undefined ? suite : await Suite.findById(suite);
  if (!doc) throw new Error('Suite no encontrada');

  let experiences = [];
  if (experienceIds.length) {
    experiences = await Experience.find({ _id: { $in: experienceIds }, ...(onlyAvailable && { available: true }) });
  }
  const experiencesTotal = experiences.reduce((sum, e) => sum + e.price, 0);

  const branchId = doc.branch?._id || doc.branch;
  const promos = promotions ?? (await loadPromotions(branchId, checkIn, checkOut));
  const quote = quoteStay({
    basePrice: doc.basePrice,
    checkIn,
    checkOut,
    experiencesTotal,
    promotions: promos.filter((p) => String(p.branch) === String(branchId))
  });

  return {
    ...quote,
    suite: doc,
    experiences,
    season: getSeason(checkIn),
    lines: groupNights(quote.nights)
  };
};
