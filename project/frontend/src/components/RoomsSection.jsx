import { useCallback, useEffect, useMemo, useState } from 'react';
import api from '../api/client';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { formatCOP } from '../utils/format';

export default function RoomsSection() {
  const [suites, setSuites] = useState(null);
  const [error, setError] = useState(false);
  const { availability, search, startBooking } = useBookingCart();
  const { setAuthModal, isAuthenticated } = useAuth();

  const fetchSuites = useCallback(() => {
    setSuites(null);
    setError(false);
    api.get('/suites', { params: { limit: 20, sortBy: 'order', sortOrder: 'asc' } })
      .then((res) => setSuites(res.data.data))
      .catch(() => {
        setSuites([]);
        setError(true);
      });
  }, []);

  useEffect(() => {
    fetchSuites();
  }, [fetchSuites]);

  // Si el huésped ya buscó disponibilidad (BookingBar), mostramos precio y
  // disponibilidad reales para esas fechas en vez del precio base genérico.
  const availabilityMap = useMemo(() => {
    if (!availability) return null;
    const map = new Map();
    availability.availableSuites.forEach((s) => map.set(s._id, { ...s, isAvailable: true }));
    (availability.unavailableSuites || []).forEach((s) => map.set(s._id, { ...s, isAvailable: false }));
    return map;
  }, [availability]);

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
            : '18 suites de diseño exclusivo entre murallas coloniales y el mar Caribe'}
        </p>
      </div>
      <div className="rooms-grid">
        {suites.map((suite) => {
          const av = availabilityMap?.get(suite._id);
          const isUnavailable = av && !av.isAvailable;
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
                <p className="room-type">{suite.type}</p>
                <h3 className="room-name">{suite.name}</h3>
                <div className="room-chips">
                  {(suite.amenities || []).slice(0, 3).map((a) => (
                    <span className="chip" key={a}>{a}</span>
                  ))}
                  <span className="chip">{suite.size} m²</span>
                  <span className="chip">Hasta {suite.maxGuests} huéspedes</span>
                </div>
                <div className="room-price">
                  {suite.originalPrice > suite.basePrice && (
                    <span className="price-old">{formatCOP(suite.originalPrice)}</span>
                  )}
                  <strong>{formatCOP(nightly)}</strong>
                  <span> / noche</span>
                </div>
                {totalForStay && (
                  <p className="form-hint">Total por {availability.nights} noches: {formatCOP(totalForStay)}</p>
                )}
                <button
                  className="btn-book room-cta"
                  disabled={isUnavailable}
                  onClick={() => {
                    if (!isAuthenticated) {
                      setAuthModal('login');
                      return;
                    }
                    openBooking(suite);
                  }}
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
