/** Elige qué mostrar: la vigente de mayor descuento o, si no hay, la próxima en empezar. */
export const pickPromotion = (promos, today) => {
  const live = promos.filter((p) => p.startDate <= today && p.endDate >= today);
  if (live.length) return { promo: [...live].sort((a, b) => b.discountPercent - a.discountPercent)[0], live: true };
  const next = promos.filter((p) => p.startDate > today).sort((a, b) => a.startDate.localeCompare(b.startDate))[0];
  return next ? { promo: next, live: false } : null;
};
