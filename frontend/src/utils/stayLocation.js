// Sucursal donde se hospedará el huésped, con coordenadas para el mapa.
// El listado de suites trae la sucursal SIN ubicación (solo nombre/slug/zona);
// el detalle de una suite sí la trae completa. Si falta, se completa con la
// lista de sucursales que ya carga la app (/branches).
const hasCoords = (b) => typeof b?.location?.lat === 'number' && typeof b?.location?.lng === 'number';

export const resolveSuiteBranch = (suite, branches = []) => {
  const ref = suite?.branch;
  if (!ref) return null;
  if (typeof ref === 'object' && hasCoords(ref)) return ref;
  const id = typeof ref === 'object' ? ref._id : ref;
  const slug = typeof ref === 'object' ? ref.slug : null;
  const found = (branches || []).find(
    (b) => (id && String(b._id) === String(id)) || (slug && b.slug === slug)
  );
  return hasCoords(found) ? found : null;
};

// Mapa de respaldo (sin WebGL): el mapa embebido de OpenStreetMap en un <iframe>.
// bbox = minLon,minLat,maxLon,maxLat ; marker = lat,lng
export const osmEmbedUrl = (lat, lng) => {
  const dLng = 0.006;
  const dLat = 0.004;
  const bbox = [lng - dLng, lat - dLat, lng + dLng, lat + dLat].map((n) => n.toFixed(5)).join(',');
  return `https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(bbox)}&layer=mapnik&marker=${lat.toFixed(5)}%2C${lng.toFixed(5)}`;
};

export const osmLinkUrl = (lat, lng) =>
  `https://www.openstreetmap.org/?mlat=${lat.toFixed(5)}&mlon=${lng.toFixed(5)}#map=17/${lat.toFixed(5)}/${lng.toFixed(5)}`;
