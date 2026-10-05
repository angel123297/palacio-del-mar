import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP } from '../utils/format';
import AvailabilityAlternatives from './AvailabilityAlternatives.jsx';

const shortBranch = (name = '') => name.replace(/^Palacio del Mar\s*·\s*/, '');

export default function RoomsSection() {
  const [suites, setSuites] = useState(null);
  const [error, setError] = useState(false);
  const [types, setTypes] = useState([]);
  const [typeFilter, setTypeFilter] = useState('');
  const [maxPrice, setMaxPrice] = useState(0); // 0 = sin tope
  const [extras, setExtras] = useState([]);    // 'hasBalcony' | 'hasTerrace' | 'hasJacuzzi' | 'ocean'
  const { availability, search, setSearch, searchAvailability, startBooking, branch, setBranch, branches } = useBookingCart();
  const toast = useToast();

  const fetchSuites = useCallback(() => {
    setSuites(null);
    setError(false);
    api.get('/suites', {
      params: { limit: 50, sortBy: 'order', sortOrder: 'asc', ...(branch ? { branch } : {}) } })
      .then((res) => setSuites(res.data.data))
      .catch(() => {
        setSuites([]);
        setError(true);
      });
  }, [branch]);

  useEffect(() => {
    fetchSuites();
  }, [fetchSuites]);

  // Tipos de suite para el filtro (GET /suites/types)
  useEffect(() => {
    api.get('/suites/types')
      .then((res) => setTypes(res.data.data || []))
      .catch(() => setTypes([]));
  }, []);

  // Si el huésped ya buscó disponibilidad (BookingBar), mostramos precio y
  // disponibilidad reales para esas fechas en vez del precio base genérico.
  const availabilityMap = useMemo(() => {
    if (!availability) return null;
    const map = new Map();
    availability.availableSuites.forEach((s) => map.set(s._id, { ...s, isAvailable: true }));
    (availability.unavailableSuites || []).forEach((s) => map.set(s._id, { ...s, isAvailable: false }));
    return map;
  }, [availability]);

  const matchesExtra = (suite, key) =>
    key === 'ocean' ? ['ocean', 'partial_ocean'].includes(suite.view) : !!suite[key];
  const visibleSuites = suites && suites.filter((s) =>
    (!typeFilter || s.type === typeFilter) &&
    (!maxPrice || s.basePrice <= maxPrice) &&
    extras.every((k) => matchesExtra(s, k)));
  const toggleExtra = (k) => setExtras((prev) => (prev.includes(k) ? prev.filter((x) => x !== k) : [...prev, k]));
  const hasFilters = !!typeFilter || !!maxPrice || extras.length > 0;

  // Elegir unas fechas alternativas: se actualiza la búsqueda y se consulta de nuevo
  const pickDates = async ({ checkIn, checkOut }) => {
    const next = { ...search, checkIn, checkOut };
    setSearch(next);
    try {
      await searchAvailability(next);
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo consultar esas fechas');
    }
  };

  const openBooking = (suite) => {
    startBooking(suite);
  };

  if (suites === null) {
    return (
      <section id="rooms">
        <div className="rooms-header">
          <p className="sec-label">Alojamiento</p>
          <h2 className="sec-title">Nuestras suites</h2>
        </div>
        <div className="rooms-grid"><div className="spinner" /></div>
      </section>
    );
  }

  if (suites.length === 0) {
    return (
      <section id="rooms">
        <div className="rooms-header">
          <p className="sec-label">Alojamiento</p>
          <h2 className="sec-title">Nuestras suites</h2>
        </div>
        <div style={{ textAlign: 'center', padding: '3rem 1rem' }}>
          <p className="form-hint" style={{ marginBottom: '1rem' }}>
            {error
              ? 'No pudimos conectar con el servidor. Intenta de nuevo en unos segundos.'
              : 'Todavía no hay habitaciones cargadas. Vuelve pronto.'}
          </p>
          <button className="btn-outline" onClick={fetchSuites}>Reintentar</button>
        </div>
      </section>
    );
  }

  return (
    <section id="rooms">
      <div className="rooms-header">
        <p className="sec-label">Alojamiento</p>
        <h2 className="sec-title">Nuestras suites</h2>
        <p className="sec-sub">
          {availability
            ? `Disponibilidad para ${availability.nights} noche(s), del ${new Date(availability.checkIn).toLocaleDateString('es-CO')} al ${new Date(availability.checkOut).toLocaleDateString('es-CO')}`
            : 'Elige tu zona en Cartagena: murallas coloniales, Getsemaní, Bocagrande o La Boquilla'}
        </p>
      </div>
      {branches.length > 1 && (
        <div className="filter-row" role="group" aria-label="Elegir sucursal">
          <button type="button" className={`filter-btn ${branch === '' ? 'is-active' : ''}`} onClick={() => setBranch('')}>
            Todas las sucursales
          </button>
          {branches.map((b) => (
            <button
              type="button"
              key={b.slug}
              className={`filter-btn ${branch === b.slug ? 'is-active' : ''}`}
              title={b.fromPrice ? `Desde ${formatCOP(b.fromPrice)} por noche` : undefined}
              onClick={() => { setBranch(b.slug); setTypeFilter(''); }}
            >
              {shortBranch(b.name)}{b.roomTypes ? ` (${b.roomTypes})` : ''}
            </button>
          ))}
        </div>
      )}
      {types.length > 1 && (
        <div className="filter-row" role="group" aria-label="Filtrar por tipo de suite">
          <button type="button" className={`filter-btn ${typeFilter === '' ? 'is-active' : ''}`} onClick={() => setTypeFilter('')}>
            Todas
          </button>
          {types.map((t) => (
            <button
              type="button"
              key={t.name}
              className={`filter-btn ${typeFilter === t.name ? 'is-active' : ''}`}
              title={t.minPrice ? `Desde ${formatCOP(t.minPrice)} por noche` : undefined}
              onClick={() => setTypeFilter(t.name)}
            >
              {t.name} ({t.count})
            </button>
          ))}
        </div>
      )}
      <div className="filter-row" role="group" aria-label="Más filtros">
        <select aria-label="Precio máximo por noche" value={maxPrice} onChange={(e) => setMaxPrice(Number(e.target.value))}>
          <option value={0}>Cualquier precio</option>
          {[400000, 600000, 800000, 1200000, 2000000].map((n) => (
            <option key={n} value={n}>Hasta {formatCOP(n)}</option>
          ))}
        </select>
        {[['hasBalcony', 'Balcón'], ['hasTerrace', 'Terraza'], ['hasJacuzzi', 'Jacuzzi'], ['ocean', 'Vista al mar']].map(([k, label]) => (
          <button type="button" key={k} className={`filter-btn ${extras.includes(k) ? 'is-active' : ''}`}
            aria-pressed={extras.includes(k)} onClick={() => toggleExtra(k)}>{label}</button>
        ))}
        {hasFilters && (
          <button type="button" className="filter-btn" onClick={() => { setTypeFilter(''); setMaxPrice(0); setExtras([]); }}>
            Limpiar filtros
          </button>
        )}
      </div>
      <AvailabilityAlternatives
        availability={availability}
        branch={branch}
        onPickBranch={(slug) => { setBranch(slug); setTypeFilter(''); }}
        onPickDates={pickDates}
      />
      <div className="rooms-grid">
        {visibleSuites.length === 0 && <p className="muted">Ninguna habitación cumple esos filtros. Prueba quitar alguno.</p>}
        {visibleSuites.map((suite) => {
          const av = availabilityMap?.get(suite._id);
          // Con una búsqueda activa, lo que no aparece como disponible no se puede reservar
          const isUnavailable = availabilityMap ? !av?.isAvailable : false;
          const nightly = av?.pricePerNight ?? suite.seasonalPrice ?? suite.basePrice;
          const totalForStay = av?.totalPrice;

          return (
            <div className={`room-card ${isUnavailable ? 'room-unavailable' : ''}`} key={suite._id}>
              <div className="room-img-wrap">
                <img src={suite.mainImage} alt={suite.name} loading="lazy" />
                {suite.featured && <span className="room-avail badge-ok">Recomendada</span>}
                {isUnavailable && <span className="room-avail badge-few">No disponible</span>}
              </div>
              <div className="room-info">
                <p className="room-type">{suite.type}{suite.branch?.name ? ` · ${shortBranch(suite.branch.name)}` : ''}</p>
                <h3 className="room-name"><Link to={`/habitaciones/${suite._id}`}>{suite.name}</Link></h3>
                <div className="room-chips">
                  {(suite.amenities || []).slice(0, 3).map((a) => (
                    <span className="chip" key={a}>{a}</span>
                  ))}
                  <span className="chip">{suite.size} m²</span>
                  <span className="chip">Hasta {suite.maxGuests} huéspedes</span>
                </div>
                <div className="room-price">
                  {av?.priceBreakdown?.discount > 0 && availability?.nights > 0 && (
                    <span className="price-old">{formatCOP(Math.round(av.priceBreakdown.lodging / availability.nights))}</span>
                  )}
                  <strong>{formatCOP(nightly)}</strong>
                  <span> / noche</span>
                </div>
                {av?.isAvailable && av.availableUnits > 0 && av.availableUnits <= 3 && (
                  <p className="form-hint">¡Quedan {av.availableUnits} {av.availableUnits === 1 ? 'habitación' : 'habitaciones'} para tus fechas!</p>
                )}
                {totalForStay && (
                  <p className="form-hint">Total por {availability.nights} noches: {formatCOP(totalForStay)}</p>
                )}
                {av?.priceBreakdown?.discount > 0 && (
                  <p className="form-hint">{av.priceBreakdown.discountReason} · ahorras {formatCOP(av.priceBreakdown.discount)}</p>
                )}
                <Link className="room-detail-link" to={`/habitaciones/${suite._id}`}>Ver detalles</Link>
                <button
                  className="btn-book room-cta"
                  disabled={isUnavailable}
                  onClick={() => openBooking(suite)}
                >
                  {isUnavailable ? 'No disponible' : 'Reservar'}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
