import { useEffect, useMemo, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';
import { useBookingCart, todayISO } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';

const STEPS = ['Fechas', 'Experiencias', 'Datos', 'Confirmación'];

export default function BookingModal() {
  const { bookingSuite, closeBooking, search, experiences, toggleExperience } = useBookingCart();
  const { user } = useAuth();
  const toast = useToast();

  const [step, setStep] = useState(0);
  const [dates, setDates] = useState({ checkIn: search.checkIn, checkOut: search.checkOut, guests: search.guests });
  const [allExperiences, setAllExperiences] = useState([]);
  const [pricing, setPricing] = useState(null);
  const [pricingLoading, setPricingLoading] = useState(false);
  const [pricingError, setPricingError] = useState('');
  const [contact, setContact] = useState({ guestName: user?.name || '', guestEmail: user?.email || '', guestPhone: '', specialRequests: '' });
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const suite = bookingSuite;

  useEffect(() => {
    if (!suite) return;
    api.get('/experiences', { params: { limit: 12 } })
      .then((res) => setAllExperiences(res.data.data || []))
      .catch(() => setAllExperiences([]));
  }, [suite]);

  useEffect(() => {
    setContact((c) => ({ ...c, guestName: c.guestName || user?.name || '', guestEmail: c.guestEmail || user?.email || '' }));
  }, [user]);

  const experienceIds = useMemo(() => experiences.map((e) => e._id), [experiences]);

  const fetchPricing = async () => {
    if (!suite) return;
    setPricingLoading(true);
    setPricingError('');
    try {
      const res = await api.post('/suites/calculate-price', {
        suiteId: suite._id,
        checkIn: dates.checkIn,
        checkOut: dates.checkOut,
        includeExperiences: experienceIds.length > 0,
        experienceIds
      });
      setPricing(res.data.data);
    } catch (err) {
setPricingError(err.response?.data?.message || 'No se pudo calcular el precio para esta suite. Intenta de nuevo.');
      setPricing(null);
    } finally {
      setPricingLoading(false);
    }
  };

  useEffect(() => {
if (step >= 1 && suite) {
      fetchPricing();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, dates.checkIn, dates.checkOut, experienceIds.length, suite]);

  if (!suite) return null;

  const nights = Math.max(0, Math.round((new Date(dates.checkOut) - new Date(dates.checkIn)) / 86400000));

  const goNext = () => {
    if (step === 0) {
      if (nights <= 0) {
        toast.error('La fecha de salida debe ser posterior a la de entrada');
        return;
      }
      if (dates.guests > suite.maxGuests) {
        toast.error(`Esta suite admite hasta ${suite.maxGuests} huéspedes`);
        return;
      }
    }
    if (step === 2) {
      if (!contact.guestName.trim() || !contact.guestEmail.trim()) {
        toast.error('Nombre y email son obligatorios');
        return;
      }
    }
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };
  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  const submitBooking = async () => {
    setSubmitting(true);
    try {
      const res = await api.post('/bookings', {
        suiteId: suite._id,
        checkIn: dates.checkIn,
        checkOut: dates.checkOut,
        guests: dates.guests,
        experiences: experienceIds,
        ...contact
      });
      setResult(res.data.data);
      toast.success('¡Reserva creada! Revisa los próximos pasos.');
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo crear la reserva. Intenta de nuevo.');
    } finally {
      setSubmitting(false);
    }
  };

  const close = () => {
    setStep(0);
    setResult(null);
    setPricing(null);
    closeBooking();
  };

  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <div className="modal-box modal-wide booking-modal">
        <button className="modal-close" onClick={close} aria-label="Cerrar">×</button>

        {result ? (
          <div className="booking-confirmation">
            <p className="modal-eyebrow">Reserva #{String(result.booking._id).slice(-8).toUpperCase()}</p>
            <h2 className="modal-title">¡Gracias, {contact.guestName.split(' ')[0]}!</h2>
            <p className="modal-copy">Tu reserva en <strong>{suite.name}</strong> quedó registrada.</p>
            <div className="confirm-summary">
              <div><span>Check-in</span><strong>{formatDate(result.booking.checkIn)}</strong></div>
              <div><span>Check-out</span><strong>{formatDate(result.booking.checkOut)}</strong></div>
              <div><span>Total</span><strong>{formatCOP(result.booking.totalPrice)}</strong></div>
              <div><span>Estado del pago</span><strong className="pill-pending">Pendiente</strong></div>
            </div>
            <div className="payment-box">
              <p>{result.payment.message}</p>
              <a
                className="wa-btn"
                target="_blank"
                rel="noreferrer"
                href={`https://wa.me/${result.payment.whatsapp.replace(/[^0-9]/g, '')}?text=${encodeURIComponent(`Hola, quiero confirmar el pago de mi reserva #${result.payment.reference} en Palacio del Mar.`)}`}
              >
                Escribir por WhatsApp
              </a>
            </div>
            <button className="btn-outline btn-block" onClick={close}>Cerrar</button>
          </div>
        ) : (
          <>
            <div className="booking-steps">
              {STEPS.map((label, i) => (
                <div key={label} className={`booking-step ${i === step ? 'active' : ''} ${i < step ? 'done' : ''}`}>
                  <span>{i + 1}</span> {label}
                </div>
              ))}
            </div>

            <h2 className="modal-title">{suite.name}</h2>
            <p className="modal-copy">{suite.type} · {suite.size} m²</p>

            {step === 0 && (
              <div className="booking-step-body">
                <label className="field-label">Llegada</label>
                <input type="date" min={todayISO()} value={dates.checkIn}
                  onChange={(e) => setDates({ ...dates, checkIn: e.target.value })} />
                <label className="field-label">Salida</label>
                <input type="date" min={dates.checkIn} value={dates.checkOut}
                  onChange={(e) => setDates({ ...dates, checkOut: e.target.value })} />
                <label className="field-label">Huéspedes (máx. {suite.maxGuests})</label>
                <select value={dates.guests} onChange={(e) => setDates({ ...dates, guests: Number(e.target.value) })}>
                  {Array.from({ length: suite.maxGuests }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <p className="form-hint">{nights} noche(s)</p>
              </div>
            )}

            {step === 1 && (
              <div className="booking-step-body">
                <p className="form-hint">Suma experiencias a tu estadía (opcional)</p>
                <div className="exp-pick-list">
                  {allExperiences.map((exp) => {
                    const checked = experiences.some((e) => e._id === exp._id);
                    return (
                      <label className={`exp-pick ${checked ? 'checked' : ''}`} key={exp._id}>
                        <input type="checkbox" checked={checked} onChange={() => toggleExperience(exp)} />
                        <span className="exp-pick-icon">{exp.icon}</span>
                        <span className="exp-pick-name">{exp.name}</span>
                        <span className="exp-pick-price">{formatCOP(exp.price)}</span>
                      </label>
                    );
                  })}
                </div>
              </div>
            )}

            {step === 2 && (
              <div className="booking-step-body">
                <label className="field-label">Nombre completo</label>
                <input value={contact.guestName} onChange={(e) => setContact({ ...contact, guestName: e.target.value })} />
                <label className="field-label">Email</label>
                <input type="email" value={contact.guestEmail} onChange={(e) => setContact({ ...contact, guestEmail: e.target.value })} />
                <label className="field-label">Teléfono (opcional)</label>
                <input value={contact.guestPhone} onChange={(e) => setContact({ ...contact, guestPhone: e.target.value })} />
                <label className="field-label">Solicitudes especiales (opcional)</label>
                <textarea rows={3} value={contact.specialRequests}
                  onChange={(e) => setContact({ ...contact, specialRequests: e.target.value })} />
              </div>
            )}

            {step === 3 && (
              <div className="booking-step-body">
                {pricingLoading && <p className="form-hint">Calculando precio…</p>}
                {pricingError && <p className="form-error">{pricingError}</p>}
                {pricing && (
                  <div className="price-breakdown">
                    <div><span>{formatCOP(pricing.nightlyPrice)} × {pricing.dates.nights} noches</span><span>{formatCOP(pricing.subtotal)}</span></div>
                    {pricing.experiencesTotal > 0 && (
                      <div><span>Experiencias</span><span>{formatCOP(pricing.experiencesTotal)}</span></div>
                    )}
                    {pricing.discount > 0 && (
                      <div className="price-discount"><span>{pricing.discountReason}</span><span>-{formatCOP(pricing.discount)}</span></div>
                    )}
                    <div className="price-total"><span>Total</span><span>{formatCOP(pricing.total)}</span></div>
                  </div>
                )}
                <p className="form-hint">
                  Tu reserva quedará <strong>pendiente de pago</strong>. Te contactaremos por WhatsApp para confirmar
                  el depósito — no se realiza ningún cobro automático.
                </p>
              </div>
            )}

            <div className="booking-nav">
              {step > 0 && <button className="btn-outline" onClick={goBack} disabled={submitting}>Atrás</button>}
              {step < STEPS.length - 1 && <button className="btn-primary" onClick={goNext}>Continuar</button>}
              {step === STEPS.length - 1 && (
                <button className="btn-primary" onClick={submitBooking} disabled={submitting}>
                  {submitting ? 'Enviando…' : 'Confirmar reserva'}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
