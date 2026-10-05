import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../api/client';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { formatCOP, formatCalendarDate } from '../utils/format';

const METHODS = { card: 'Tarjeta', pse: 'PSE', nequi: 'Nequi' };

/** Comprobante imprimible: el botón abre el diálogo del navegador ("Guardar como PDF"). */
export default function ReceiptPage() {
  const { id } = useParams();
  const { branches } = useBookingCart();
  const [booking, setBooking] = useState(null);
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.get(`/bookings/${id}`), api.get(`/payments/booking/${id}`)])
      .then(([b, p]) => {
        setBooking(b.data.data);
        setPayment((p.data.data || []).find((x) => x.status === 'approved' || x.status === 'refunded') || null);
      })
      .catch((err) => setError(err.response?.data?.message || 'No se pudo cargar el comprobante'));
  }, [id]);

  if (error) return <div className="simple-page"><div className="simple-card"><p>{error}</p><Link className="btn-primary" to="/dashboard">Mis reservas</Link></div></div>;
  if (!booking) return <div className="simple-page"><div className="spinner" /></div>;

  const branch = branches.find((b) => b._id === (booking.branch?._id || booking.branch));
  const paid = booking.paymentStatus === 'paid' || booking.paymentStatus === 'partial' || booking.paymentStatus === 'refunded';
  const code = String(booking._id).slice(-8).toUpperCase();

  return (
    <div className="receipt-page">
      <div className="receipt-actions no-print">
        <Link className="btn-outline" to={`/reservas/${id}`}>← Volver</Link>
        <button className="btn-primary" onClick={() => window.print()}>Imprimir / Guardar como PDF</button>
      </div>
      <article className="receipt">
        <header>
          <h1>Palacio del Mar</h1>
          <p>{branch ? `${branch.name} · ${branch.address}` : 'Cartagena de Indias'}</p>
        </header>
        <h2>{paid ? 'Comprobante de reserva' : 'Resumen de reserva (sin pago registrado)'}</h2>
        <table>
          <tbody>
            <tr><th>Reserva</th><td>#{code}</td></tr>
            {payment?.receiptNumber && <tr><th>Comprobante</th><td>{payment.receiptNumber}</td></tr>}
            <tr><th>Huésped</th><td>{booking.guestName || '—'} · {booking.guestEmail || '—'}</td></tr>
            <tr><th>Habitación</th><td>{booking.suite?.name} ({booking.suite?.type})</td></tr>
            <tr><th>Llegada</th><td>{formatCalendarDate(booking.checkIn)}{branch ? ` · desde las ${branch.checkInTime}` : ''}</td></tr>
            <tr><th>Salida</th><td>{formatCalendarDate(booking.checkOut)}{branch ? ` · hasta las ${branch.checkOutTime}` : ''}</td></tr>
            <tr><th>Noches / huéspedes</th><td>{booking.nights} / {booking.guests}{booking.children > 0 ? ` (${booking.children} niños)` : ''}</td></tr>
          </tbody>
        </table>
        <table>
          <tbody>
            <tr><th>Alojamiento</th><td>{formatCOP(booking.subtotal)}</td></tr>
            {booking.experiencesTotal > 0 && <tr><th>Experiencias</th><td>{formatCOP(booking.experiencesTotal)}</td></tr>}
            {booking.discount > 0 && <tr><th>{booking.discountReason || 'Descuento'}</th><td>− {formatCOP(booking.discount)}</td></tr>}
            <tr className="receipt-total"><th>Total</th><td>{formatCOP(booking.totalPrice)}</td></tr>
            {payment && <tr><th>Pagado con</th><td>{METHODS[payment.method] || payment.method}{payment.simulated ? ' (simulado)' : ''}</td></tr>}
            {booking.amountRefunded > 0 && <tr><th>Reembolsado</th><td>{formatCOP(booking.amountRefunded)}</td></tr>}
          </tbody>
        </table>
        {payment?.simulated && <p className="receipt-note">Documento de demostración: el pago fue simulado y no tiene validez fiscal.</p>}
      </article>
    </div>
  );
}
