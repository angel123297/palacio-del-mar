import { useEffect, useMemo, useRef, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';
import { useBookingCart, todayISO, readDraft, writeDraft, clearDraft } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';
import HoldNotice from './HoldNotice.jsx';
import { contactFromUser, contactAfterUserChange, userKey } from '../utils/contact.js';

const STEPS = ['Fechas', 'Experiencias', 'Datos', 'Confirmación'];

const SEASON_NAMES = { low: 'temporada baja', mid: 'temporada media', high: 'temporada alta', peak: 'temporada pico' };

export default function BookingModal() {
  const { bookingSuite, closeBooking, search, experiences, toggleExperience } = useBookingCart();
  const { user, isAuthenticated, authModal, setAuthModal } = useAuth();
  const toast = useToast();

  const draft = useRef(readDraft()).current; // borrador de un refresco anterior (si lo hay)
  const [step, setStep] = useState(draft?.step ?? 0);
  const [dates, setDates] = useState(draft?.dates ?? { checkIn: search.checkIn, checkOut: search.checkOut, guests: search.guests });
  const pendingSubmit = useRef(false); // el usuario pulsó "Confirmar" sin sesión
  const submitRef = useRef(null);
  const skipReset = useRef(!!draft?.suite);
  const [allExperiences, setAllExperiences] = useState([]);
  const [pricing, setPricing] = useState(null);
  const [pricingLoading, setPricingLoading] = useState(false);
  const [pricingError, setPricingError] = useState('');
  const [contact, setContact] = useState(() => contactFromUser(user));
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const suite = bookingSuite;

  useEffect(() => {
    if (!suite) return;
    api.get('/experiences', { params: { limit: 12 } })
      .then((res) => setAllExperiences(res.data.data || []))
      .catch(() => setAllExperiences([]));
  }, [suite]);

  // Este modal está siempre montado: su estado sobrevive a un cierre de sesión.
  // Al cambiar la sesión se limpian los datos del usuario anterior (ver utils/contact.js).
  const userKeyRef = useRef(userKey(user));
  useEffect(() => {
    const prev = userKeyRef.current;
    const next = userKey(user);
    if (prev === next) return;
    userKeyRef.current = next;
    setContact((c) => contactAfterUserChange(prev, user, c));
    if (prev !== null) {
      // salió o cambió de usuario: nada de su reserva queda a la vista
      setResult(null);
      setPricing(null);
      clearDraft();
    }
  }, [user]);

  // Cada vez que se abre una suite nueva se parte de las fechas buscadas
  // (salvo al restaurar un borrador tras refrescar la página).
  useEffect(() => {
    if (!suite) return;
    if (skipReset.current) { skipReset.current = false; return; }
    setDates({ checkIn: search.checkIn, checkOut: search.checkOut, guests: search.guests });
    setStep(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [suite?._id]);

  // Guarda el borrador mientras la reserva está abierta y sin confirmar
  useEffect(() => {
    if (suite && !result) writeDraft({ suite, dates, step });
  }, [suite, dates, step, result]);

  // Si pidió confirmar sin sesión, al registrarse/entrar se confirma solo
  useEffect(() => {
    if (!pendingSubmit.current) return;
    if (user) {
      pendingSubmit.current = false;
      submitRef.current?.();
    } else if (!authModal) {
      pendingSubmit.current = false; // cerró el modal sin entrar: vuelve a la confirmación
    }
  }, [user, authModal]);

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
      if (!contact.guestPhone.trim()) {
        toast.error('El teléfono es obligatorio');
        return;
      }
    }
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };
  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  const submitBooking = async () => {
    if (!isAuthenticated) {
      // Recién aquí se pide la cuenta; la selección se conserva en este modal
      pendingSubmit.current = true;
      setAuthModal('register');
      return;
    }
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
      clearDraft();
      toast.success(res.data.data.existing ? 'Ya tenías esta reserva pendiente: no se creó otra.' : '¡Reserva creada! Revisa los próximos pasos.');
    } catch (err) {
      toast.error(
        err.response?.status === 409
          ? 'Esa habitación acaba de ocuparse para esas fechas. Prueba con otras fechas u otra sucursal.'
          : err.response?.data?.message || 'No se pudo crear la reserva. Intenta de nuevo.'
      );
    } finally {
      setSubmitting(false);
    }
  };

  submitRef.current = submitBooking;

  const close = () => {
    clearDraft();
    setStep(0);
    setResult(null);
    setPricing(null);
    closeBooking();
  };

  // Mientras se muestra el registro/login se oculta este modal (conserva su estado)
  if (authModal && !user) return null;

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
            <HoldNotice booking={result.booking} />
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
                <label className="field-label">Teléfono</label>
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
                    {(pricing.lines?.length ? pricing.lines : [{ unitPrice: pricing.nightlyPrice, nights: pricing.dates.nights, amount: pricing.subtotal }]).map((l, i) => (
                      <div key={i}>
                        <span>{formatCOP(l.unitPrice)} × {l.nights} {l.nights === 1 ? 'noche' : 'noches'}{l.season ? ` · ${SEASON_NAMES[l.season]}` : ''}</span>
                        <span>{formatCOP(l.amount)}</span>
                      </div>
                    ))}
                    {pricing.experiencesTotal > 0 && (
                      <div><span>Experiencias</span><span>{formatCOP(pricing.experiencesTotal)}</span></div>
                    )}
                    {pricing.discount > 0 && (
                      <div className="price-discount"><span>{pricing.discountReason}</span><span>-{formatCOP(pricing.discount)}</span></div>
                    )}
                    <div className="price-total"><span>Total</span><span>{formatCOP(pricing.total)}</span></div>
                  </div>
                )}
                {!isAuthenticated && (
                  <p className="form-hint">Para confirmar necesitamos que crees tu cuenta o inicies sesión. Tu selección se conserva.</p>
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
                  {submitting ? 'Enviando…' : isAuthenticated ? 'Confirmar reserva' : 'Confirmar y crear cuenta'}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
