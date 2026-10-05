import { lazy, Suspense, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { useBookingCart, todayISO } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import BookingModal from '../components/BookingModal.jsx';
import SuiteGallery from '../components/SuiteGallery.jsx';
import { formatCOP } from '../utils/format.js';
import { SEASON_NAMES } from '../utils/checkout.js';

const BranchMap = lazy(() => import('../components/BranchMap.jsx'));

const BED_LABELS = { single: 'individual', double: 'doble', queen: 'queen', king: 'king', super_king: 'super king' };
const VIEW_LABELS = {
  city: 'Vista a la ciudad', garden: 'Vista al jardín', pool: 'Vista a la piscina', ocean: 'Vista al mar',
  partial_ocean: 'Vista parcial al mar', landmark: 'Vista a la ciudad amurallada', courtyard: 'Vista al patio'
};
const POI_ICONS = { historia: '🏛️', playa: '🏖️', gastronomia: '🍽️', 'vida-nocturna': '🌙', cultura: '🎭', compras: '🛍️', naturaleza: '🌴' };
const shortBranch = (name = '') => name.replace(/^Palacio del Mar\s*·\s*/, '');

export default function SuiteDetailPage() {
  const { id } = useParams();
  const { search, setSearch, startBooking } = useBookingCart();
  const toast = useToast();
  const [suite, setSuite] = useState(null);
  const [related, setRelated] = useState([]);
  const [status, setStatus] = useState('loading'); // loading | ok | missing | error
  const [dates, setDates] = useState({ checkIn: search.checkIn, checkOut: search.checkOut });
  const [children, setChildren] = useState(search.children || 0);
  const [adults, setAdults] = useState(Math.max(1, (search.guests || 2) - (search.children || 0)));

  const nights = useMemo(() => {
    const d = Math.round((new Date(dates.checkOut) - new Date(dates.checkIn)) / 86400000);
    return Number.isFinite(d) && d > 0 ? d : 0;
  }, [dates]);
  const validDates = nights > 0 && dates.checkIn >= todayISO();

  // Un único pedido: la suite y, si las fechas son válidas, la cotización real de esas noches
  useEffect(() => {
    let alive = true;
    api.get(`/suites/${id}`, { params: validDates ? { checkIn: dates.checkIn, checkOut: dates.checkOut } : {} })
      .then((res) => {
        if (!alive) return;
        setSuite(res.data.data);
        setRelated(res.data.related || []);
        setStatus('ok');
      })
      .catch((err) => {
        if (!alive) return;
        setStatus(err.response?.status === 404 || err.response?.status === 400 ? 'missing' : 'error');
      });
    return () => { alive = false; };
  }, [id, dates.checkIn, dates.checkOut, validDates]);

  useEffect(() => { window.scrollTo(0, 0); }, [id]);

  const guests = adults + children;
  const tooMany = suite && guests > suite.maxGuests;
  const quote = suite?.quote;

  const reserve = () => {
    if (!validDates) { toast.error('Elige unas fechas válidas (la salida debe ser posterior a la llegada)'); return; }
    if (tooMany) { toast.error(`Esta habitación admite hasta ${suite.maxGuests} huéspedes`); return; }
    setSearch({ ...search, checkIn: dates.checkIn, checkOut: dates.checkOut, guests, children });
    startBooking(suite);
  };

  if (status === 'loading') {
    return <div className="dashboard-page"><Navbar /><div className="dashboard-shell"><div className="spinner" /></div></div>;
  }
  if (status !== 'ok') {
    return (
      <div className="dashboard-page">
        <Navbar />
        <div className="dashboard-shell">
          <p className="muted">{status === 'missing' ? 'No encontramos esta habitación.' : 'No pudimos cargar la habitación. Intenta de nuevo.'}</p>
          <Link className="btn-primary" to="/#rooms">Ver todas las habitaciones</Link>
        </div>
      </div>
    );
  }

  const bedText = (suite.beds || [])
    .map((b) => `${b.quantity} ${b.quantity === 1 ? 'cama' : 'camas'} ${BED_LABELS[b.type] || b.type}`).join(' + ');
  const branch = suite.branch;
  const extras = [
    suite.hasBalcony && 'Balcón', suite.hasTerrace && 'Terraza', suite.hasJacuzzi && 'Jacuzzi'
  ].filter(Boolean);
  const rating = suite.reviewCount > 0 ? `${suite.averageRating.toFixed(1)} · ${suite.reviewCount} reseñas` : null;

  return (
    <div className="dashboard-page">
      <Navbar />
      <div className="dashboard-shell suite-detail">
        <nav className="crumbs" aria-label="Ruta">
          <Link to="/#rooms">Habitaciones</Link>
          {branch && <> › <span>{shortBranch(branch.name)}</span></>} › <span>{suite.name}</span>
        </nav>

        <SuiteGallery images={[suite.mainImage, ...(suite.images || [])]} name={suite.name} />

        <div className="detail-cols">
          <div className="detail-main">
            <p className="room-type">{suite.type}{branch ? ` · ${shortBranch(branch.name)}` : ''}</p>
            <h1 className="sec-title">{suite.name}</h1>
            <div className="room-chips">
              <span className="chip">{suite.size} {suite.sizeUnit || 'm²'}</span>
              <span className="chip">Hasta {suite.maxGuests} huéspedes</span>
              {bedText && <span className="chip">{bedText}</span>}
              <span className="chip">{suite.bathrooms} {suite.bathrooms === 1 ? 'baño' : 'baños'}</span>
              {suite.view && <span className="chip">{VIEW_LABELS[suite.view] || suite.view}</span>}
              {extras.map((e) => <span className="chip" key={e}>{e}</span>)}
              {rating && <span className="chip">★ {rating}</span>}
            </div>
            <p className="detail-desc">{suite.description}</p>

            {(suite.amenities?.length > 0 || suite.features?.length > 0) && (
              <>
                <h2 className="detail-h">Qué incluye</h2>
                <ul className="detail-list">
                  {[...(suite.amenities || []), ...(suite.features || [])].map((a) => <li key={a}>{a}</li>)}
                </ul>
              </>
            )}

            {branch && (
              <>
                <h2 className="detail-h">Dónde queda</h2>
                <p className="muted">{branch.address} · {branch.zone}</p>
                <Suspense fallback={<div className="branch-map"><div className="spinner" /></div>}>
                  <BranchMap branch={branch} />
                </Suspense>
                {branch.highlights?.length > 0 && (
                  <ul className="poi-list">
                    {branch.highlights.map((p) => (
                      <li key={p.name}><span>{POI_ICONS[p.type] || '📍'} {p.name}</span><span>{p.walkMinutes} min a pie</span></li>
                    ))}
                  </ul>
                )}
                <p className="form-hint">Check-in {branch.checkInTime} · Check-out {branch.checkOutTime}</p>
              </>
            )}
          </div>

          <aside className="detail-book">
            <div className="detail-price">
              {quote ? (
                <><strong>{formatCOP(Math.round(quote.lodging / quote.nights))}</strong><span> / noche (promedio)</span></>
              ) : (
                <><strong>{formatCOP(suite.seasonalPrice)}</strong><span> / noche</span></>
              )}
            </div>
            {!quote && <p className="form-hint">{suite.priceNote}. Elige fechas para ver el total exacto.</p>}

            <label className="field-label" htmlFor="d-in">Llegada</label>
            <input id="d-in" type="date" min={todayISO()} value={dates.checkIn}
              onChange={(e) => {
                const val = e.target.value;
                const today = todayISO();
                const newIn = val && val < today ? today : (val || today);
                const addOne = (iso) => {
                  const [y, m, d] = iso.split('-').map(Number);
                  return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
                };
                const newOut = dates.checkOut <= newIn ? addOne(newIn) : dates.checkOut;
                setDates({ ...dates, checkIn: newIn, checkOut: newOut });
              }} />
            <label className="field-label" htmlFor="d-out">Salida</label>
            <input id="d-out" type="date" min={dates.checkIn} value={dates.checkOut}
              onChange={(e) => {
                const val = e.target.value;
                const addOne = (iso) => {
                  const [y, m, d] = iso.split('-').map(Number);
                  return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
                };
                const newOut = val && val <= dates.checkIn ? addOne(dates.checkIn) : (val || addOne(dates.checkIn));
                setDates({ ...dates, checkOut: newOut });
              }} />
            <div className="detail-guests">
              <div>
                <label className="field-label" htmlFor="d-ad">Adultos</label>
                <select id="d-ad" value={adults} onChange={(e) => setAdults(Number(e.target.value))}>
                  {Array.from({ length: suite.maxGuests }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
              </div>
              <div>
                <label className="field-label" htmlFor="d-ch">Niños</label>
                <select id="d-ch" value={children} onChange={(e) => setChildren(Number(e.target.value))}>
                  {Array.from({ length: Math.max(1, suite.maxGuests) }, (_, i) => i).map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
              </div>
            </div>
            {tooMany && <p className="form-error">Esta habitación admite hasta {suite.maxGuests} huéspedes.</p>}

            {quote && validDates && (
              <div className="detail-quote">
                {quote.lines.map((l) => (
                  <div className="quote-line" key={`${l.season}-${l.unitPrice}`}>
                    <span>{formatCOP(l.unitPrice)} × {l.nights} {l.nights === 1 ? 'noche' : 'noches'} · {SEASON_NAMES[l.season]}</span>
                    <span>{formatCOP(l.amount)}</span>
                  </div>
                ))}
                {quote.discount > 0 && (
                  <div className="quote-line quote-discount"><span>{quote.discountReason}</span><span>−{formatCOP(quote.discount)}</span></div>
                )}
                <div className="quote-line quote-total"><span>Total</span><span>{formatCOP(quote.totalPrice)}</span></div>
              </div>
            )}
            {suite.unitsLeft === 0 && <p className="form-error">No hay habitaciones libres en esas fechas.</p>}
            {suite.unitsLeft > 0 && suite.unitsLeft <= 3 && (
              <p className="form-hint">¡Quedan {suite.unitsLeft} {suite.unitsLeft === 1 ? 'habitación' : 'habitaciones'} de este tipo!</p>
            )}
            <button className="btn-book room-cta" disabled={suite.unitsLeft === 0 || !suite.available} onClick={reserve}>
              {suite.unitsLeft === 0 ? 'No disponible' : 'Reservar'}
            </button>
          </aside>
        </div>

        {related.length > 0 && (
          <>
            <h2 className="detail-h">Otras habitaciones de esta sucursal</h2>
            <div className="related-grid">
              {related.map((r) => (
                <Link className="related-card" to={`/habitaciones/${r._id}`} key={r._id}>
                  <img src={r.mainImage} alt={r.name} loading="lazy" />
                  <div><strong>{r.name}</strong><span>{formatCOP(r.basePrice)} / noche</span></div>
                </Link>
              ))}
            </div>
          </>
        )}
      </div>
      <Footer />
      <BookingModal />
    </div>
  );
}
