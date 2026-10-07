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
