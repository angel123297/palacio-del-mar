// Resumen por sucursal para unas fechas: cuántos tipos de habitación hay
// libres, desde qué precio y la mejor promoción vigente. Funciones puras
// (sin base de datos) para poder probarlas.

/** Agrupa las suites (ya con isAvailable y pricePerNight) por sucursal. */
export const summarizeBranches = (suites) => {
  const byBranch = new Map();
  for (const s of suites) {
    const b = s.branch;
    if (!b || !b._id) continue; // suite sin sucursal poblada: se ignora
    const key = String(b._id);
    if (!byBranch.has(key)) {
      byBranch.set(key, { _id: b._id, slug: b.slug, name: b.name, zone: b.zone, availableSuites: 0, fromPrice: null, promotion: null });
    }
    const row = byBranch.get(key);
    if (s.isAvailable) {
      row.availableSuites += 1;
      row.fromPrice = row.fromPrice === null ? s.pricePerNight : Math.min(row.fromPrice, s.pricePerNight);
    }
  }
  return [...byBranch.values()];
};

/** Asigna a cada sucursal su mejor promoción (mayor descuento). */
export const attachPromotions = (rows, promotions) => {
  for (const row of rows) {
    const best = promotions
      .filter((p) => String(p.branch) === String(row._id))
      .sort((a, b) => b.discountPercent - a.discountPercent)[0];
    row.promotion = best
      ? { title: best.title, discountPercent: best.discountPercent, startDate: best.startDate, endDate: best.endDate }
      : null;
  }
  return rows;
};
