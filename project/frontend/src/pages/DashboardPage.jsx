import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';

const STATUS_CLASS = {
  pending: 'pill-pending',
  confirmed: 'pill-ok',
  cancelled: 'pill-cancelled',
  completed: 'pill-ok',
  'no-show': 'pill-cancelled'
};

function ModifyDatesForm({ booking, onDone }) {
  const [newCheckIn, setNewCheckIn] = useState(booking.checkIn.slice(0, 10));
  const [newCheckOut, setNewCheckOut] = useState(booking.checkOut.slice(0, 10));
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.put(`/bookings/${booking._id}/modify-dates`, { newCheckIn, newCheckOut });
      toast.success('Fechas actualizadas');
      onDone(true);
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudieron modificar las fechas');
      setBusy(false);
    }
  };

  return (
    <form className="inline-edit-form" onSubmit={submit}>
      <input type="date" value={newCheckIn} onChange={(e) => setNewCheckIn(e.target.value)} required />
      <input type="date" value={newCheckOut} min={newCheckIn} onChange={(e) => setNewCheckOut(e.target.value)} required />
      <button className="btn-primary" type="submit" disabled={busy}>{busy ? 'Guardando…' : 'Guardar'}</button>
      <button type="button" className="link-btn" onClick={() => onDone(false)}>Cancelar</button>
    </form>
  );
}

function BookingCard({ booking, onChanged }) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);

  const cancel = async () => {
    if (!window.confirm('¿Seguro que quieres cancelar esta reserva?')) return;
    setBusy(true);
    try {
      const res = await api.put(`/bookings/${booking._id}/cancel`, {});
      const refund = res.data?.data?.refundAmount || 0;
      toast.success(refund > 0
        ? `Reserva cancelada. Reembolso estimado: ${refund.toLocaleString('es-CO')} (se procesa manualmente)`
        : 'Reserva cancelada');
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
            <p className="form-hint">Reserva #{String(booking._id).slice(-8).toUpperCase()}</p>
          </div>
          <div className="pill-group">
            <span className={`pill ${STATUS_CLASS[booking.status] || 'pill-pending'}`}>{booking.statusLabel || booking.status}</span>
            <span className={`pill ${booking.paymentStatus === 'paid' ? 'pill-ok' : 'pill-pending'}`}>
              {booking.paymentStatusLabel || booking.paymentStatus}
            </span>
          </div>
        </div>

        {editing ? (
          <ModifyDatesForm booking={booking} onDone={(changed) => { setEditing(false); if (changed) onChanged(); }} />
        ) : (
          <div className="booking-dates-row">
            <span><strong>Check-in</strong> {formatDate(booking.checkIn)}</span>
            <span><strong>Check-out</strong> {formatDate(booking.checkOut)}</span>
            <span><strong>Huéspedes</strong> {booking.guests}</span>
            <span><strong>Total</strong> {formatCOP(booking.totalPrice)}</span>
          </div>
        )}

        {booking.experiences?.length > 0 && (
          <p className="form-hint">Experiencias: {booking.experiences.map((e) => e.name).join(', ')}</p>
        )}

        {!editing && (
          <div className="booking-card-actions">
            {booking.isModifiable && (
              <button className="btn-outline" onClick={() => setEditing(true)} disabled={busy}>Modificar fechas</button>
            )}
            {booking.isCancellable && (
              <button className="btn-outline btn-danger" onClick={cancel} disabled={busy}>Cancelar reserva</button>
            )}
            {!booking.isModifiable && !booking.isCancellable && (
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
      // Antes se guardaba la respuesta completa (res.data, con
      // "success" incluido) directo en el estado de "reservas", así que
      // .map() sobre un objeto tiraba un error de JavaScript y la
      // pantalla completa de "Mis reservas" quedaba en blanco. La forma
      // real es res.data.data.bookings.
      .then((res) => setBookings(res.data.data.bookings))
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
          <Link className="btn-outline" to="/">← Volver al sitio</Link>
        </div>

        <div className="dashboard-filters">
          {['all', 'pending', 'confirmed', 'completed', 'cancelled'].map((f) => (
            <button
              key={f}
              className={`filter-chip ${filter === f ? 'active' : ''}`}
              onClick={() => setFilter(f)}
            >
              {{ all: 'Todas', pending: 'Pendientes', confirmed: 'Confirmadas', completed: 'Completadas', cancelled: 'Canceladas' }[f]}
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
