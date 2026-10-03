// Promociones de EJEMPLO (relativas a la fecha en que se ejecuta el seed).
// Cada sucursal tiene una ventana distinta para poder probar las
// sugerencias de "otra sucursal con promoción".
const DAY = 86400000;
const midnightUTC = (d) => new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));

const plan = [
  { slug: 'getsemani', title: 'Escápate a Getsemaní', discountPercent: 15, from: 0, to: 120 },
  { slug: 'bocagrande', title: 'Sol y playa en Bocagrande', discountPercent: 10, from: 0, to: 90 },
  { slug: 'la-boquilla', title: 'Brisa de La Boquilla', discountPercent: 20, from: 14, to: 120 },
  { slug: 'centro-historico', title: 'Noches entre murallas', discountPercent: 8, from: 30, to: 75 }
];

export const buildSamplePromotions = (branchesBySlug, now = new Date()) => {
  const today = midnightUTC(now);
  return plan
    .filter((p) => branchesBySlug.get(p.slug))
    .map((p) => ({
      branch: branchesBySlug.get(p.slug)._id,
      title: p.title,
      discountPercent: p.discountPercent,
      startDate: new Date(today.getTime() + p.from * DAY),
      endDate: new Date(today.getTime() + p.to * DAY),
      active: true,
      isSample: true
    }));
};
