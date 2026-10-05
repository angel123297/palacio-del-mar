import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import HoldNotice from '../components/HoldNotice.jsx';
import usePaymentConfig from '../hooks/usePaymentConfig.js';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatCalendarDate } from '../utils/format';
import { groupNights, SEASON_NAMES, policyText, METHOD_NOTES, newIdempotencyKey } from '../utils/checkout.js';

const FALLBACK_METHODS = [
  { id: 'card', label: 'Tarjeta de crédito o débito' },
  { id: 'pse', label: 'PSE (débito desde tu banco)' },
  { id: 'nequi', label: 'Nequi' }
];

// Centro de pago. Hoy el cobro es SIMULADO (siempre aprueba); el backend decide
// el proveedor, esta pantalla no cambia cuando llegue una pasarela real.
export default function CheckoutPage() {
  const { id } = useParams();
  const toast = useToast();
  const config = usePaymentConfig();
  const [booking, setBooking] = useState(null);
  const [error, setError] = useState('');
  const [method, setMethod] = useState('card');
  const [paying, setPaying] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [failure, setFailure] = useState(null);
  const keyRef = useRef(newIdempotencyKey()); // un doble clic reutiliza la misma clave

  const load = useCallback(async () => {
    try {
      const res = await api.get(`/bookings/${id}`);
      setBooking(res.data.data);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo cargar la reserva');
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const pay = async () => {
    setPaying(true);
    setFailure(null);
    try {
      const res = await api.post('/payments/checkout', { bookingId: booking._id, method, idempotencyKey: keyRef.current });
      setReceipt(res.data.data.payment);
      toast.success('¡Pago aprobado!');
      load();
    } catch (err) {
      const data = err.response?.data;
      setFailure({ message: data?.message || 'No se pudo procesar el pago. Intenta de nuevo.', code: data?.code });
      if (data?.code === 'DECLINED') keyRef.current = newIdempotencyKey(); // rechazo: el siguiente intento es nuevo
      if (['HOLD_EXPIRED', 'NOT_PAYABLE', 'ALREADY_PAID'].includes(data?.code)) load();
    } finally {
      setPaying(false);
    }
  };

  const methods = config?.methods?.length ? config.methods : FALLBACK_METHODS;
  const lines = groupNights(booking?.pricing?.nights);
  const isPaid = booking && (booking.paymentStatus === 'paid' || !!receipt);
  const isPayable = booking && booking.status === 'pending' && booking.paymentStatus !== 'paid' && !receipt;

  return (
    <div className="dashboard-page checkout-page">
      <Navbar />
      <div className="dashboard-shell">
        <div className="dashboard-head">
          <div>
            <p className="sec-label">Centro de pago</p>
            <h1 className="sec-title">Finaliza tu reserva</h1>
          </div>
          <div className="dashboard-head-actions">
            <Link className="btn-outline" to="/dashboard">← Mis reservas</Link>
          </div>
        </div>

        {config?.simulated && (
          <div className="sim-banner" role="note">
            <strong>MODO SIMULADO.</strong> No ingreses datos reales: el pago siempre se aprueba y no se cobra nada.
          </div>
        )}

        {error && <div className="form-error">{error}</div>}
        {!booking && !error && <div className="spinner" />}

        {booking && isPaid && (
          <section className="checkout-card receipt">
            <p className="modal-eyebrow">Reserva #{String(booking._id).slice(-8).toUpperCase()}</p>
            <h2 className="modal-title">¡Pago aprobado!</h2>
            <p className="modal-copy">Tu reserva en <strong>{booking.suite?.name}</strong> está confirmada.</p>
            <div className="confirm-summary">
              {receipt?.receiptNumber && <div><span>Comprobante</span><strong>{receipt.receiptNumber}</strong></div>}
              <div><span>Pagado</span><strong>{formatCOP(receipt?.amount ?? booking.amountPaid ?? booking.totalPrice)}</strong></div>
              <div><span>Check-in</span><strong>{formatCalendarDate(booking.checkIn)}</strong></div>
              <div><span>Check-out</span><strong>{formatCalendarDate(booking.checkOut)}</strong></div>
            </div>
            <p className="form-hint">Te enviamos el comprobante por correo.</p>
            <div className="booking-card-actions">
              <Link className="btn-primary" to={`/reservas/${booking._id}`}>Ver mi reserva</Link>
              <Link className="btn-outline" to="/dashboard">Mis reservas</Link>
            </div>
          </section>
        )}

        {booking && !isPaid && !isPayable && (
          <section className="checkout-card">
            <h2 className="modal-title">Esta reserva ya no se puede pagar</h2>
            <p className="modal-copy">
              {booking.status === 'expired'
                ? 'Venció porque no se pagó a tiempo y la habitación volvió a estar disponible. No se te cobró nada.'
                : 'Su estado actual no permite el pago.'}
            </p>
            <div className="booking-card-actions">
              <Link className="btn-primary" to="/">Buscar de nuevo</Link>
              <Link className="btn-outline" to="/dashboard">Mis reservas</Link>
            </div>
          </section>
        )}

        {booking && isPayable && (
          <div className="checkout-grid">
            <div className="checkout-main">
              <section className="checkout-card">
                <h3>Datos del huésped</h3>
                <div className="booking-dates-row">
                  <span><strong>A nombre de</strong> {booking.guestName || '—'}</span>
                  <span><strong>Correo</strong> {booking.guestEmail || '—'}</span>
                  <span><strong>Teléfono</strong> {booking.guestPhone || '—'}</span>
                </div>
              </section>

              <section className="checkout-card">
                <h3>Tu estadía</h3>
                <p className="form-hint">{booking.suite?.name}</p>
                <div className="booking-dates-row">
                  <span><strong>Check-in</strong> {formatCalendarDate(booking.checkIn)}</span>
                  <span><strong>Check-out</strong> {formatCalendarDate(booking.checkOut)}</span>
                  <span><strong>Noches</strong> {booking.nights}</span>
                  <span><strong>Huéspedes</strong> {booking.guests}{booking.children > 0 ? ` (${booking.children} ${booking.children === 1 ? 'niño' : 'niños'})` : ''}</span>
                </div>
              </section>

              <section className="checkout-card">
                <h3>Método de pago</h3>
                <div className="method-list" role="radiogroup" aria-label="Método de pago">
                  {methods.map((m) => (
                    <label key={m.id} className={`method-option ${method === m.id ? 'is-selected' : ''}`}>
                      <input type="radio" name="method" value={m.id} checked={method === m.id} onChange={() => setMethod(m.id)} />
                      <span>
                        <strong>{m.label}</strong>
                        <small>{METHOD_NOTES[m.id]}</small>
                      </span>
                    </label>
                  ))}
                </div>
              </section>

              <section className="checkout-card">
                <h3>Política de cancelación</h3>
                <p className="form-hint">{policyText(config?.cancellationPolicy) || 'Cargando política…'}</p>
              </section>
            </div>

            <aside className="checkout-card checkout-summary">
              <h3>Resumen</h3>
              <HoldNotice booking={booking} />
              <div className="price-lines">
                {lines.length > 0 ? lines.map((l, i) => (
                  <div key={i}>
                    <span>{formatCOP(l.unitPrice)} × {l.nights} {l.nights === 1 ? 'noche' : 'noches'}{l.season ? ` · ${SEASON_NAMES[l.season]}` : ''}</span>
                    <span>{formatCOP(l.amount)}</span>
                  </div>
                )) : (
                  <div><span>Alojamiento</span><span>{formatCOP(booking.subtotal)}</span></div>
                )}
                {(booking.experiences || []).map((e) => (
                  <div key={e._id}><span>{e.name}</span><span>{formatCOP(e.price)}</span></div>
                ))}
                {booking.discount > 0 && (
                  <div className="price-discount"><span>{booking.discountReason || 'Descuento'}</span><span>-{formatCOP(booking.discount)}</span></div>
                )}
                <div className="price-total"><span>Total</span><span>{formatCOP(booking.totalPrice)}</span></div>
              </div>

              {failure && (
                <div className="form-error">
                  {failure.message}
                  {failure.code === 'HOLD_EXPIRED' && <> <Link to="/">Buscar de nuevo</Link></>}
                </div>
              )}

              <button className="btn-primary btn-block" onClick={pay} disabled={paying}>
                {paying ? 'Procesando…' : `Pagar ${formatCOP(booking.totalPrice)}`}
              </button>
              <p className="form-hint">Al pagar confirmas que leíste la política de cancelación.</p>
            </aside>
          </div>
        )}
      </div>
      <Footer />
    </div>
  );
}
