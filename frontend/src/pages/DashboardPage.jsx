import HoldNotice from '../components/HoldNotice.jsx';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import UpcomingBookings from '../components/UpcomingBookings.jsx';
import { useAuth } from '../context/AuthContext.jsx';
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

function ModifyDatesForm({ booking, onDone }) {
  const bId = booking._id || booking.id;
  const [newCheckIn, setNewCheckIn] = useState(booking.checkIn?.slice(0, 10) || '');
  const [newCheckOut, setNewCheckOut] = useState(booking.checkOut?.slice(0, 10) || '');
  const [guests, setGuests] = useState(booking.guests || 2);
  const [children, setChildren] = useState(booking.children || 0);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const res = await api.put(`/bookings/${bId}/modify-dates`, {
        checkIn: newCheckIn,
        checkOut: newCheckOut,
        newCheckIn,
        newCheckOut,
        guests: Number(guests),
        children: Number(children)
      });
      toast.success(res.data?.message || 'Reserva actualizada');
      onDone(true);
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo modificar la reserva');
      setBusy(false);
    }
  };

  return (
    <form className="inline-edit-form" onSubmit={submit} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '8px', alignItems: 'center' }}>
      <p className="form-hint" style={{ gridColumn: '1 / -1', margin: '0 0 4px 0' }}>
        Nota: Modifica fechas o número de huéspedes. El saldo se recalculará automáticamente.
      </p>
      <div>
        <label className="field-label" style={{ fontSize: '0.8rem' }}>Check-in</label>
        <input type="date" value={newCheckIn} onChange={(e) => setNewCheckIn(e.target.value)} required />
      </div>
      <div>
        <label className="field-label" style={{ fontSize: '0.8rem' }}>Check-out</label>
        <input type="date" value={newCheckOut} min={newCheckIn} onChange={(e) => setNewCheckOut(e.target.value)} required />
      </div>
      <div>
        <label className="field-label" style={{ fontSize: '0.8rem' }}>Adultos</label>
        <select value={guests} onChange={(e) => setGuests(e.target.value)}>
          {[1, 2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>{n} {n === 1 ? 'adulto' : 'adultos'}</option>)}
        </select>
      </div>
      <div>
        <label className="field-label" style={{ fontSize: '0.8rem' }}>Niños</label>
        <select value={children} onChange={(e) => setChildren(e.target.value)}>
          {[0, 1, 2, 3, 4].map((n) => <option key={n} value={n}>{n} {n === 1 ? 'niño' : 'niños'}</option>)}
        </select>
      </div>
      <div style={{ display: 'flex', gap: '8px', gridColumn: '1 / -1', marginTop: '4px' }}>
        <button className="btn-primary" type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar Cambios'}</button>
        <button type="button" className="link-btn" onClick={() => onDone(false)}>Cancelar</button>
      </div>
    </form>
  );
}

function BookingCard({ booking, onChanged }) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const bId = booking._id || booking.id;

  const cancel = async () => {
    if (!window.confirm('¿Seguro que quieres cancelar esta reserva?')) return;
    setBusy(true);
    try {
      const res = await api.post(`/bookings/${bId}/cancel`, {});
      toast.success('Reserva cancelada');
      onChanged();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo cancelar la reserva');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="booking-card">
      <img
        src={booking.suite?.mainImage || 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=400&auto=format&fit=crop&q=80'}
        alt={booking.suite?.name}
      />
      <div className="booking-card-body">
        <div className="booking-card-head">
          <div>
            <h3>{booking.suite?.name || 'Suite'}</h3>
            <p className="form-hint">Reserva #{booking.bookingCode || String(bId).slice(-8).toUpperCase()}</p>
          </div>
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

        {editing ? (
          <ModifyDatesForm booking={booking} onDone={(changed) => { setEditing(false); if (changed) onChanged(); }} />
        ) : (
          <div className="booking-dates-row">
            <span><strong>Check-in</strong> {formatCalendarDate(booking.checkIn)}</span>
            <span><strong>Check-out</strong> {formatCalendarDate(booking.checkOut)}</span>
            <span><strong>Huéspedes</strong> {booking.guests}{booking.children > 0 ? ` (${booking.children} ${booking.children === 1 ? 'niño' : 'niños'})` : ''}</span>
            <span><strong>Total</strong> {formatCOP(booking.totalPrice)}</span>
          </div>
        )}

        {booking.experiences?.length > 0 && (
          <p className="form-hint">Experiencias: {booking.experiences.map((e) => e.name).join(', ')}</p>
        )}

        {!editing && (
          <div className="booking-card-actions">
            {(!['cancelled', 'expired', 'completed'].includes(booking.status) && (booking.paymentStatus !== 'paid' || (booking.balanceDue && booking.balanceDue > 0))) && (
              <Link className="btn-primary" to={`/pagar/${bId}`}>
                Pagar ahora {booking.balanceDue > 0 ? `(${formatCOP(booking.balanceDue)})` : ''}
              </Link>
            )}
            <Link className="btn-outline" to={`/reservas/${bId}`}>Ver detalle</Link>
            {(booking.isModifiable !== false && !['cancelled', 'expired', 'completed'].includes(booking.status)) && (
              <button className="btn-outline" onClick={() => setEditing(true)} disabled={busy}>Modificar fechas</button>
            )}
            {((booking.isCancellable ?? booking.isCancelable) !== false && !['cancelled', 'expired', 'completed'].includes(booking.status)) && (
              <button className="btn-outline btn-danger" onClick={cancel} disabled={busy}>Cancelar reserva</button>
            )}
            {['cancelled', 'expired', 'completed'].includes(booking.status) && (
              <span className="form-hint">Esta reserva ya no admite cambios.</span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState(null);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('all');

  const load = useCallback(() => {
    api.get('/bookings', { params: filter === 'all' ? {} : { status: filter } })
      .then((res) => {
        const raw = res.data.data;
        setBookings(Array.isArray(raw) ? raw : raw?.bookings || []);
      })
      .catch((err) => setError(err.response?.data?.message || 'No se pudieron cargar tus reservas'));
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="dashboard-page">
      <Navbar />
      <div className="dashboard-shell">
        <div className="dashboard-head">
          <div>
            <p className="sec-label">Mi cuenta</p>
            <h1 className="sec-title">Hola, {user?.name?.split(' ')[0]}</h1>
          </div>
          <div className="dashboard-head-actions">
            <Link className="btn-outline" to="/perfil">Mi perfil</Link>
            <Link className="btn-outline" to="/">← Volver al sitio</Link>
          </div>
        </div>

        <UpcomingBookings reloadKey={bookings} />
        <div className="dashboard-filters">
          {['all', 'pending', 'confirmed', 'completed', 'cancelled', 'expired'].map((f) => (
            <button
              key={f}
              className={`filter-chip ${filter === f ? 'active' : ''}`}
              onClick={() => setFilter(f)}
            >
              {{ all: 'Todas', pending: 'Pendientes', confirmed: 'Confirmadas', completed: 'Completadas', cancelled: 'Canceladas', expired: 'Vencidas' }[f]}
            </button>
          ))}
        </div>

        {error && <div className="form-error">{error}</div>}

        {bookings === null && !error && <div className="spinner" />}

        {bookings && bookings.length === 0 && (
          <div className="empty-state">
            <p>Todavía no tienes reservas{filter !== 'all' ? ' en este estado' : ''}.</p>
            <Link className="btn-primary" to="/#rooms">Explorar suites</Link>
          </div>
        )}

        {bookings && bookings.length > 0 && (
          <div className="bookings-list">
            {bookings.map((b) => (
              <BookingCard key={b._id} booking={b} onChanged={load} />
            ))}
          </div>
        )}
      </div>
      <Footer />
    </div>
  );
}
