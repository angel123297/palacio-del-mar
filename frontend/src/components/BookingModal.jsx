import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';
import { useAuth } from '../context/AuthContext.jsx';
import { useBookingCart, todayISO, readDraft, writeDraft, clearDraft } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';
import usePaymentConfig from '../hooks/usePaymentConfig.js';
import { policyText } from '../utils/checkout.js';
import { contactFromUser, contactAfterUserChange, userKey } from '../utils/contact.js';
import { resolveSuiteBranch } from '../utils/stayLocation.js';

// El mapa (MapLibre) se descarga solo cuando el huésped llega al paso de confirmación
const BranchMap = lazy(() => import('./BranchMap.jsx'));

const STEPS = ['Fechas', 'Experiencias', 'Datos', 'Confirmación'];

const SEASON_NAMES = { low: 'temporada baja', mid: 'temporada media', high: 'temporada alta', peak: 'temporada pico' };

export default function BookingModal() {
  const { bookingSuite, closeBooking, search, experiences, toggleExperience, branches } = useBookingCart();
  const { user, isAuthenticated, authModal, setAuthModal } = useAuth();
  const navigate = useNavigate();
  const paymentConfig = usePaymentConfig();
  const toast = useToast();

  const draft = useRef(readDraft()).current; // borrador de un refresco anterior (si lo hay)
  const [step, setStep] = useState(draft?.step ?? 0);
  const [dates, setDates] = useState(draft?.dates ?? { checkIn: search.checkIn, checkOut: search.checkOut, guests: search.guests, children: search.children || 0 });
  const pendingSubmit = useRef(false); // el usuario pulsó "Confirmar" sin sesión
  const submitRef = useRef(null);
  const skipReset = useRef(!!draft?.suite);
  const [allExperiences, setAllExperiences] = useState([]);
  const [pricing, setPricing] = useState(null);
  const [pricingLoading, setPricingLoading] = useState(false);
  const [pricingError, setPricingError] = useState('');
  const [contact, setContact] = useState(() => contactFromUser(user));
  const [submitting, setSubmitting] = useState(false);

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
      setPricing(null);
      clearDraft();
    }
  }, [user]);

  // Cada vez que se abre una suite nueva se parte de las fechas buscadas
  // (salvo al restaurar un borrador tras refrescar la página).
  useEffect(() => {
    if (!suite) return;
    if (skipReset.current) { skipReset.current = false; return; }
    setDates({ checkIn: search.checkIn, checkOut: search.checkOut, guests: search.guests, children: search.children || 0 });
    setStep(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [suite?._id]);

  // Guarda el borrador mientras la reserva está abierta y sin confirmar
  useEffect(() => {
    if (suite) writeDraft({ suite, dates, step });
  }, [suite, dates, step]);

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

  // Dónde se hospedará (para la previsualización del mapa)
  const stayBranch = useMemo(() => resolveSuiteBranch(suite, branches), [suite, branches]);

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
        children: dates.children || 0,
        experiences: experienceIds,
        ...contact
      });
      const bookingId = res.data.data.booking._id;
      closeAndReset();
      toast.success(res.data.data.existing ? 'Ya tenías esta reserva pendiente: continúa con el pago.' : 'Te guardamos la habitación. Completa el pago.');
      navigate(`/pagar/${bookingId}`);
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

  const closeAndReset = () => {
    clearDraft();
    setStep(0);
    setPricing(null);
    closeBooking();
  };
  const close = closeAndReset;

  // Mientras se muestra el registro/login se oculta este modal (conserva su estado)
  if (authModal && !user) return null;

  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <div className="modal-box modal-wide booking-modal">
        <button className="modal-close" onClick={close} aria-label="Cerrar">×</button>

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
                <label className="field-label">Salida</label>
                <input type="date" min={dates.checkIn} value={dates.checkOut}
                  onChange={(e) => {
                    const val = e.target.value;
                    const addOne = (iso) => {
                      const [y, m, d] = iso.split('-').map(Number);
                      return new Date(Date.UTC(y, m - 1, d + 1)).toISOString().slice(0, 10);
                    };
                    const newOut = val && val <= dates.checkIn ? addOne(dates.checkIn) : (val || addOne(dates.checkIn));
                    setDates({ ...dates, checkOut: newOut });
                  }} />
                <label className="field-label">Adultos</label>
                <select
                  value={Math.max(1, dates.guests - (dates.children || 0))}
                  onChange={(e) => setDates({ ...dates, guests: Number(e.target.value) + (dates.children || 0) })}
                >
                  {Array.from({ length: suite.maxGuests }, (_, i) => i + 1).map((n) => (
                    <option key={n} value={n}>{n}</option>
                  ))}
                </select>
                <label className="field-label">Niños (máx. {suite.maxGuests} huéspedes en total)</label>
                <select
                  value={dates.children || 0}
                  onChange={(e) => {
                    const kids = Number(e.target.value);
                    setDates({ ...dates, children: kids, guests: Math.max(1, dates.guests - (dates.children || 0)) + kids });
                  }}
                >
                  {Array.from({ length: Math.max(1, suite.maxGuests) }, (_, i) => i).map((n) => (
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
                {stayBranch && (
                  <div className="stay-location">
                    <h3 className="stay-location-title">Dónde te hospedarás</h3>
                    <p className="form-hint">{stayBranch.address}{stayBranch.zone ? ` · ${stayBranch.zone}` : ''}</p>
                    <Suspense fallback={<div className="branch-map"><div className="spinner" /></div>}>
                      <BranchMap branch={stayBranch} />
                    </Suspense>
                    {(stayBranch.checkInTime || stayBranch.checkOutTime) && (
                      <p className="form-hint">Check-in {stayBranch.checkInTime} · Check-out {stayBranch.checkOutTime}</p>
                    )}
                  </div>
                )}
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
                  Al continuar te guardamos la habitación mientras pagas en el siguiente paso. No se cobra nada hasta que pagues.
                </p>
                {paymentConfig?.cancellationPolicy && (
                  <p className="form-hint">{policyText(paymentConfig.cancellationPolicy)}</p>
                )}
              </div>
            )}

            <div className="booking-nav">
              {step > 0 && <button className="btn-outline" onClick={goBack} disabled={submitting}>Atrás</button>}
              {step < STEPS.length - 1 && <button className="btn-primary" onClick={goNext}>Continuar</button>}
              {step === STEPS.length - 1 && (
                <button className="btn-primary" onClick={submitBooking} disabled={submitting}>
                  {submitting ? 'Enviando…' : isAuthenticated ? 'Continuar al pago' : 'Continuar y crear cuenta'}
                </button>
              )}
            </div>
          </>
      </div>
    </div>
  );
}
