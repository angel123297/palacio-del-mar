import { useEffect, useState } from 'react';
import api from '../api/client';
import { formatCOP } from '../utils/format';

const shortBranch = (name = '') => name.replace(/^Palacio del Mar\s*·\s*/, '');
const day = (iso) => new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'short', timeZone: 'UTC' });

/**
 * Se muestra solo cuando, tras buscar disponibilidad, la sucursal elegida (o
 * todas) no tiene habitaciones libres. Ofrece las mismas noches en fechas
 * cercanas y las otras sucursales que sí tienen lugar (con su promoción).
 */
export default function AvailabilityAlternatives({ availability, branch, onPickBranch, onPickDates }) {
  const summary = availability?.branchSummary || [];
  const current = branch ? summary.find((b) => b.slug === branch) : null;
  const emptyBranch = !!branch && !!current && current.availableSuites === 0;
  const emptyAll = !branch && !!availability && availability.stats?.availableSuites === 0;

  const checkIn = availability?.checkIn?.slice(0, 10);
  const checkOut = availability?.checkOut?.slice(0, 10);
  const guests = availability?.filters?.guests;
  const [alt, setAlt] = useState(null);

  // Fechas alternativas DE LA SUCURSAL elegida (el backend las calcula al filtrar por sucursal)
  useEffect(() => {
    setAlt(null);
    if (!emptyBranch) return undefined;
    let cancelled = false;
    api.get('/availability', { params: { checkIn, checkOut, branch, ...(guests ? { guests } : {}) } })
      .then((res) => !cancelled && setAlt(res.data.data.alternativeDates || []))
      .catch(() => !cancelled && setAlt([]));
    return () => { cancelled = true; };
  }, [emptyBranch, branch, checkIn, checkOut, guests]);

  if (!emptyBranch && !emptyAll) return null;

  const alternatives = emptyAll ? availability.alternativeDates || [] : alt;
  const nights = availability.nights;
  const others = summary
    .filter((b) => b.slug !== branch && b.availableSuites > 0)
    .sort((a, b) => (b.promotion?.discountPercent || 0) - (a.promotion?.discountPercent || 0) || a.fromPrice - b.fromPrice);

  return (
    <div className="alt-panel">
      <h3>
        {emptyBranch
          ? `No quedan habitaciones libres en ${shortBranch(current.name)} para esas ${nights} noche(s)`
          : 'No quedan habitaciones libres en ninguna sucursal para esas fechas'}
      </h3>

      {alternatives === null && <p className="form-hint">Buscando fechas cercanas…</p>}
      {alternatives && alternatives.length > 0 && (
        <>
          <p className="form-hint">Las mismas {nights} noche(s) en otras fechas:</p>
          <div className="filter-row">
            {alternatives.map((a) => (
              <button
                type="button"
                className="filter-btn"
                key={a.checkIn}
                onClick={() => onPickDates({ checkIn: a.checkIn.slice(0, 10), checkOut: a.checkOut.slice(0, 10) })}
              >
                {day(a.checkIn)} – {day(a.checkOut)} · {a.availableSuites} tipo(s)
              </button>
            ))}
          </div>
        </>
      )}
      {alternatives && alternatives.length === 0 && (
        <p className="form-hint">No encontramos esas mismas noches libres en ±7 días.</p>
      )}

      {emptyBranch && others.length > 0 && (
        <>
          <p className="form-hint">En tus fechas sí hay lugar en:</p>
          <div className="alt-branches">
            {others.map((b) => (
              <button type="button" className="alt-branch" key={b.slug} onClick={() => onPickBranch(b.slug)}>
                <strong>{shortBranch(b.name)}</strong>
                <span>{b.availableSuites} tipo(s) desde {formatCOP(b.fromPrice)}</span>
                {b.promotion && (
                  <em className="alt-promo">-{b.promotion.discountPercent}% · {b.promotion.title}</em>
                )}
              </button>
            ))}
          </div>
          {others.some((b) => b.promotion) && (
            <p className="form-hint">Promociones de ejemplo (datos de prueba): el precio mostrado ya las incluye cuando te ahorran más que el descuento por estadía larga.</p>
          )}
        </>
      )}
    </div>
  );
}
