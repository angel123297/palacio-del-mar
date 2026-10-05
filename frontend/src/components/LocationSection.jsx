import { lazy, Suspense } from 'react';
import { useBookingCart } from '../context/BookingCartContext.jsx';

const BranchMap = lazy(() => import('./BranchMap.jsx'));
const shortBranch = (name = '') => name.replace(/^Palacio del Mar\s*·\s*/, '');
const POI_ICONS = { historia: '🏛️', playa: '🏖️', gastronomia: '🍽️', 'vida-nocturna': '🌙', cultura: '🎭', compras: '🛍️', naturaleza: '🌴' };

/** Ubicación de la sucursal elegida (la misma del buscador y de las habitaciones). */
export default function LocationSection() {
  const { branches, branch, setBranch } = useBookingCart();
  const current = branches.find((b) => b.slug === branch) || branches[0];

  if (!current) return null;

  return (
    <section id="location">
      <div className="location-content">
        <p className="sec-label">Ubicación</p>
        <h2 className="sec-title">{shortBranch(current.name)}</h2>
        {branches.length > 1 && (
          <div className="filter-row" role="group" aria-label="Elegir sucursal">
            {branches.map((b) => (
              <button
                type="button"
                key={b.slug}
                className={`filter-btn ${b.slug === current.slug ? 'is-active' : ''}`}
                onClick={() => setBranch(b.slug)}
              >
                {shortBranch(b.name)}
              </button>
            ))}
          </div>
        )}
        <p>{current.description}</p>
        <p className="form-hint">{current.address} · {current.zone}</p>
        <div className="location-items">
          {(current.highlights || []).slice(0, 4).map((p) => (
            <div className="loc-item" key={p.name}>
              <span className="loc-icon">{POI_ICONS[p.type] || '📍'}</span>
              <span>{p.name} · {p.walkMinutes} min a pie</span>
            </div>
          ))}
        </div>
      </div>
      <div className="location-map">
        <Suspense fallback={<div className="spinner" />}>
          <BranchMap branch={current} />
        </Suspense>
      </div>
    </section>
  );
}
