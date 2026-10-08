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
import { paymentBalance } from '../utils/checkout.js';

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
    const expId = exp._id || exp.id;
    setBusyId(expId);
    try {
      const res = await api.post(`/bookings/${id}/experiences`, { experienceId: expId });
      const due = res.data?.data?.balanceDue || 0;
      toast.success(due > 0
        ? `${exp.name} agregada. Tienes un saldo de ${formatCOP(due)} por pagar.`
        : `${exp.name} agregada a tu reserva`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo agregar la experiencia');
    } finally {
      setBusyId(null);
    }
  };

  const removeExperience = async (exp) => {
    const expId = exp._id || exp.id || exp.experienceId;
    if (!window.confirm(`¿Quitar "${exp.name}" de tu reserva?`)) return;
    setBusyId(expId);
    try {
      const res = await api.delete(`/bookings/${id}/experiences/${expId}`);
      const refund = res.data?.settlement?.refundAmount || 0;
      toast.info(refund > 0
        ? `${exp.name} quitada. Se registró un reembolso de ${formatCOP(refund)}.`
        : `${exp.name} quitada de tu reserva`);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo quitar la experiencia');
    } finally {
      setBusyId(null);
    }
  };

  const mine = booking?.experiences || [];
  const mineIds = new Set(mine.map((e) => e._id || e.id || e.experienceId));
  const available = catalog.filter((e) => e.available !== false && !mineIds.has(e._id || e.id));
  const canEdit = booking?.isModifiable !== false && !['cancelled', 'expired', 'completed'].includes(booking?.status);
  const full = mine.length >= MAX_EXPERIENCES;
  const bookingCodeDisplay = booking?.bookingCode || String(booking?._id || booking?.id || '').slice(-8).toUpperCase();

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
                <p className="form-hint">Reserva #{bookingCodeDisplay}</p>
                <div className="pill-group">
                  <span className={`pill ${STATUS_CLASS[booking.status] || 'pill-pending'}`}>{booking.statusLabel || booking.status}</span>
                  <span className={`pill ${booking.paymentStatus === 'paid' ? 'pill-ok' : (booking.paymentStatus === 'refunded' ? 'pill-cancelled' : 'pill-pending')}`}>
                    {booking.paymentStatus === 'refunded' ? 'REEMBOLSADO' : (booking.paymentStatusLabel || booking.paymentStatus?.toUpperCase())}
                  </span>
                  {!['cancelled', 'expired', 'completed'].includes(booking.status) && booking.paymentStatus !== 'paid' && (
                    <span className="pill pill-pending" style={{ borderColor: 'var(--gold)', color: 'var(--gold)' }}>⚠️ Pago pendiente</span>
                  )}
                </div>
              </div>
              <HoldNotice booking={booking} />

              {booking.status === 'cancelled' && (
                <p className="form-hint" style={{ marginTop: 8, color: 'var(--gold)', fontWeight: 500 }}>
                  {booking.paymentStatus === 'refunded' || (booking.refundedAmount && booking.refundedAmount > 0) || (booking.paidAmount && booking.paidAmount > 0)
                    ? `ℹ️ Reserva cancelada. El reembolso del dinero abonado (${formatCOP(booking.refundedAmount || booking.paidAmount)}) será transferido a tu cuenta bancaria / método de pago en un plazo de 24 a 48 horas.`
                    : 'Reserva cancelada sin cargos.'}
                </p>
              )}
              {(!['cancelled', 'expired', 'completed'].includes(booking.status) && (booking.paymentStatus !== 'paid' || (booking.balanceDue && booking.balanceDue > 0))) && (
                <div className="booking-card-actions">
                  <Link className="btn-primary" to={`/pagar/${booking._id || booking.id}`}>
                    Pagar ahora / Completar saldo {booking.balanceDue > 0 ? `(${formatCOP(booking.balanceDue)})` : ''}
                  </Link>
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
                {booking.paymentStatus === 'partial' && (() => {
                  // Misma cuenta que la pantalla de pago y que el servidor (neto de devoluciones)
                  const { alreadyPaid, remaining } = paymentBalance(booking);
                  return (
                    <>
                      <div><span>Ya pagado</span><span>{formatCOP(alreadyPaid)}</span></div>
                      <div><span><strong>Saldo por pagar</strong></span><span><strong>{formatCOP(remaining)}</strong></span></div>
                    </>
                  );
                })()}
              </div>
            </section>

            <section className="detail-card detail-wide">
              <h3 className="profile-form-title">Experiencias de tu reserva</h3>
              {mine.length === 0 && <p className="form-hint">Aún no has agregado experiencias.</p>}
              {mine.map((exp, idx) => {
                const eId = exp._id || exp.id || exp.experienceId || idx;
                return (
                  <div className="exp-row" key={eId}>
                    <span>{exp.name}</span>
                    <span>{formatCOP(exp.price)}</span>
                    {canEdit && (
                      <button className="link-btn danger" onClick={() => removeExperience(exp)} disabled={busyId === eId}>
                        Quitar
                      </button>
                    )}
                  </div>
                );
              })}

              {canEdit && available.length > 0 && (
                <>
                  <h3 className="profile-form-title" style={{ marginTop: 22 }}>Agregar experiencias</h3>
                  {full && <p className="form-hint">Llegaste al máximo de {MAX_EXPERIENCES} experiencias por reserva.</p>}
                  {available.map((exp) => {
                    const eId = exp._id || exp.id;
                    return (
                      <div className="exp-row" key={eId}>
                        <span>{exp.name}</span>
                        <span>{formatCOP(exp.price)}</span>
                        <button className="btn-outline" onClick={() => addExperience(exp)} disabled={full || busyId === eId}>
                          {busyId === eId ? 'Agregando…' : 'Agregar'}
                        </button>
                      </div>
                    );
                  })}
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
