import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import HoldNotice from '../components/HoldNotice.jsx';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { downloadIcs } from '../utils/calendar.js';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatCalendarDate } from '../utils/format';

const STATUS_CLASS = {
  pending: 'pill-pending',
  confirmed: 'pill-ok',
  cancelled: 'pill-cancelled',
  expired: 'pill-cancelled',
  completed: 'pill-ok',
  'no-show': 'pill-cancelled'
};
const MAX_EXPERIENCES = 5;

export default function BookingDetailPage() {
  const { id } = useParams();
  const toast = useToast();
  const { branches } = useBookingCart();
  const [booking, setBooking] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    api.get(`/bookings/${id}`)
      .then((res) => { setBooking(res.data.data); setError(''); })
      .catch((err) => setError(err.response?.data?.message || 'No se pudo cargar la reserva'));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    api.get('/experiences', { params: { limit: 50 } })
      .then((res) => setCatalog(res.data.data || []))
      .catch(() => setCatalog([]));
  }, []);

  const addExperience = async (exp) => {
    setBusyId(exp._id);
    try {
      await api.post(`/bookings/${id}/experiences`, { experienceId: exp._id });
      toast.success(`${exp.name} agregada a tu reserva`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo agregar la experiencia');
    } finally {
      setBusyId(null);
    }
  };

  const removeExperience = async (exp) => {
    if (!window.confirm(`¿Quitar "${exp.name}" de tu reserva?`)) return;
    setBusyId(exp._id);
    try {
      await api.delete(`/bookings/${id}/experiences/${exp._id}`);
      toast.info(`${exp.name} quitada de tu reserva`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo quitar la experiencia');
    } finally {
      setBusyId(null);
    }
  };

  const mine = booking?.experiences || [];
  const mineIds = new Set(mine.map((e) => e._id));
  const available = catalog.filter((e) => e.available !== false && !mineIds.has(e._id));
  const canEdit = !!booking?.isModifiable;
  const full = mine.length >= MAX_EXPERIENCES;

  return (
    <div className="dashboard-page">
      <Navbar />
      <div className="dashboard-shell">
        <div className="dashboard-head">
          <div>
            <p className="sec-label">Detalle de reserva</p>
            <h1 className="sec-title">{booking?.suite?.name || 'Reserva'}</h1>
          </div>
          <div className="dashboard-head-actions">
            <Link className="btn-outline" to="/dashboard">← Mis reservas</Link>
          </div>
        </div>

        {error && <div className="form-error">{error}</div>}
        {!booking && !error && <div className="spinner" />}

        {booking && (
          <div className="detail-grid">
            <section className="detail-card">
              <div className="booking-card-head">
                <p className="form-hint">Reserva #{String(booking._id).slice(-8).toUpperCase()}</p>
                <div className="pill-group">
                  <span className={`pill ${STATUS_CLASS[booking.status] || 'pill-pending'}`}>{booking.statusLabel || booking.status}</span>
                  <span className={`pill ${booking.paymentStatus === 'paid' ? 'pill-ok' : 'pill-pending'}`}>
                    {booking.paymentStatusLabel || booking.paymentStatus}
                  </span>
                  {!['cancelled', 'expired', 'completed'].includes(booking.status) && booking.paymentStatus !== 'paid' && (
                    <span className="pill pill-pending" style={{ borderColor: 'var(--gold)', color: 'var(--gold)' }}>⚠️ Pago pendiente</span>
                  )}
                </div>
              </div>
              <HoldNotice booking={booking} />
              {!['cancelled', 'expired', 'completed'].includes(booking.status) && booking.paymentStatus !== 'paid' && (
                <div className="booking-card-actions">
                  <Link className="btn-primary" to={`/pagar/${booking._id}`}>Pagar ahora / Completar saldo</Link>
                </div>
              )}
              {['confirmed', 'completed'].includes(booking.status) && (
                <div className="booking-card-actions">
                  <button className="btn-outline" onClick={() => downloadIcs(booking, branches.find((b) => b._id === (booking.branch?._id || booking.branch)))}>
                    Agregar a mi calendario (.ics)
                  </button>
                  <Link className="btn-outline" to={`/reservas/${booking._id}/comprobante`}>Comprobante (PDF)</Link>
                </div>
              )}
              <div className="booking-dates-row">
                <span><strong>Check-in</strong> {formatCalendarDate(booking.checkIn)}</span>
                <span><strong>Check-out</strong> {formatCalendarDate(booking.checkOut)}</span>
                <span><strong>Noches</strong> {booking.nights}</span>
                <span><strong>Huéspedes</strong> {booking.guests}{booking.children > 0 ? ` (${booking.children} ${booking.children === 1 ? 'niño' : 'niños'})` : ''}</span>
              </div>
              <div className="booking-dates-row">
                <span><strong>A nombre de</strong> {booking.guestName || '—'}</span>
                <span><strong>Correo</strong> {booking.guestEmail || '—'}</span>
                <span><strong>Teléfono</strong> {booking.guestPhone || '—'}</span>
              </div>
              {booking.specialRequests && (
                <p className="form-hint">Solicitudes especiales: {booking.specialRequests}</p>
              )}
            </section>

            <section className="detail-card">
              <h3 className="profile-form-title">Resumen de pago</h3>
              <div className="price-rows">
                <div><span>Alojamiento</span><span>{formatCOP(booking.subtotal)}</span></div>
                <div><span>Experiencias</span><span>{formatCOP(booking.experiencesTotal)}</span></div>
                {booking.discount > 0 && (
                  <div><span>{booking.discountReason || 'Descuento'}</span><span>− {formatCOP(booking.discount)}</span></div>
                )}
                <div className="price-total"><span>Total</span><span>{formatCOP(booking.totalPrice)}</span></div>
              </div>
            </section>

            <section className="detail-card detail-wide">
              <h3 className="profile-form-title">Experiencias de tu reserva</h3>
              {mine.length === 0 && <p className="form-hint">Aún no has agregado experiencias.</p>}
              {mine.map((exp) => (
                <div className="exp-row" key={exp._id}>
                  <span>{exp.name}</span>
                  <span>{formatCOP(exp.price)}</span>
                  {canEdit && (
                    <button className="link-btn danger" onClick={() => removeExperience(exp)} disabled={busyId === exp._id}>
                      Quitar
                    </button>
                  )}
                </div>
              ))}

              {canEdit && available.length > 0 && (
                <>
                  <h3 className="profile-form-title" style={{ marginTop: 22 }}>Agregar experiencias</h3>
                  {full && <p className="form-hint">Llegaste al máximo de {MAX_EXPERIENCES} experiencias por reserva.</p>}
                  {available.map((exp) => (
                    <div className="exp-row" key={exp._id}>
                      <span>{exp.name}</span>
                      <span>{formatCOP(exp.price)}</span>
                      <button className="btn-outline" onClick={() => addExperience(exp)} disabled={full || busyId === exp._id}>
                        {busyId === exp._id ? 'Agregando…' : 'Agregar'}
                      </button>
                    </div>
                  ))}
                </>
              )}
              {!canEdit && <p className="form-hint">Esta reserva ya no admite cambios.</p>}
            </section>
          </div>
        )}
      </div>
      <Footer />
    </div>
  );
}
