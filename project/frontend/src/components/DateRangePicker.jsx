import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import api from '../api/client';

const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const WEEKDAYS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
const MAX_NIGHTS = 90;
const MAX_YEAR = 2030; // el backend solo acepta años 2020-2030

const pad = (n) => String(n).padStart(2, '0');
const toISO = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const addDays = (iso, n) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};
const localToday = () => {
  const t = new Date();
  return toISO(t.getFullYear(), t.getMonth() + 1, t.getDate());
};
const nightsBetween = (a, b) => Math.round((new Date(b) - new Date(a)) / 86400000);
const prettyDate = (iso) => {
  if (!iso) return 'Elegir fecha';
  const [y, m, d] = iso.split('-').map(Number);
  return `${d} ${MONTHS[m - 1].slice(0, 3)} ${y}`;
};

/**
 * Selector de rango de fechas que muestra los días sin disponibilidad
 * (GET /availability/monthly) y la temporada alta (GET /availability/peak-dates).
 * El servidor sigue siendo quien valida la reserva: esto solo orienta al huésped.
 */
export default function DateRangePicker({ checkIn, checkOut, guests, onChange }) {
  const today = useMemo(localToday, []);
  const [open, setOpen] = useState(false);
  const [view, setView] = useState(() => {
    const base = checkIn || today;
    return { year: Number(base.slice(0, 4)), month: Number(base.slice(5, 7)) };
  });
  // 'in' = el próximo clic elige llegada; 'out' = elige salida
  const [step, setStep] = useState('in');
  const [soldOut, setSoldOut] = useState({}); // { 'YYYY-MM-DD': true }
  const [loaded, setLoaded] = useState({});   // { 'guests:YYYY-M': true }
  const [peakRanges, setPeakRanges] = useState([]);
  const [peakYears, setPeakYears] = useState({});
  const [loading, setLoading] = useState(false);
  const wrapRef = useRef(null);

  // Cerrar al hacer clic fuera o con Escape
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  // Los datos dependen del nº de huéspedes (suites con capacidad suficiente)
  useEffect(() => { setSoldOut({}); setLoaded({}); }, [guests]);

  const loadMonth = useCallback(async (year, month) => {
    const key = `${guests}:${year}-${month}`;
    if (loaded[key]) return;
    setLoading(true);
    try {
      const res = await api.get('/availability/monthly', { params: { year, month, guests } });
      const days = res.data?.data?.calendar || [];
      setSoldOut((prev) => {
        const next = { ...prev };
        days.forEach((d) => { if (d.soldOut) next[d.date] = true; });
        return next;
      });
      setLoaded((prev) => ({ ...prev, [key]: true }));
    } catch {
      // Si falla, el calendario sigue siendo usable: el servidor valida al buscar.
    } finally {
      setLoading(false);
    }
  }, [guests, loaded]);

  const loadPeak = useCallback(async (year) => {
    if (peakYears[year]) return;
    setPeakYears((p) => ({ ...p, [year]: true }));
    try {
      const res = await api.get('/availability/peak-dates', { params: { year } });
      const d = res.data?.data || {};
      const ranges = [...(d.highSeason || []), ...(d.peakSeason || [])];
      setPeakRanges((prev) => [...prev, ...ranges]);
    } catch { /* decorativo: si falla, no se marca temporada alta */ }
  }, [peakYears]);

  useEffect(() => {
    if (!open) return;
    loadMonth(view.year, view.month);
    // Navidad/Año Nuevo cruza de año: se pide también el año anterior
    loadPeak(view.year);
    loadPeak(view.year - 1);
  }, [open, view, loadMonth, loadPeak]);

  const isPeak = (iso) => peakRanges.some((r) => iso >= r.start && iso <= r.end);

  // Una salida es válida si ninguna NOCHE entre llegada y salida está agotada.
  const canCheckOut = (inISO, outISO) => {
    if (outISO <= inISO) return false;
    const n = nightsBetween(inISO, outISO);
    if (n > MAX_NIGHTS) return false;
    for (let i = 0; i < n; i++) if (soldOut[addDays(inISO, i)]) return false;
    return true;
  };

  const pick = (iso) => {
    if (step === 'out' && checkIn && canCheckOut(checkIn, iso)) {
      onChange({ checkIn, checkOut: iso });
      setOpen(false);
      setStep('in');
      return;
    }
    // Nueva llegada (también si la salida elegida no era válida)
    if (soldOut[iso] || iso < today) return;
    onChange({ checkIn: iso, checkOut: '' });
    setStep('out');
  };

  const shift = (delta) => {
    setView((v) => {
      let m = v.month + delta;
      let y = v.year;
      if (m < 1) { m = 12; y -= 1; }
      if (m > 12) { m = 1; y += 1; }
      return { year: y, month: m };
    });
  };

  const todayY = Number(today.slice(0, 4));
  const todayM = Number(today.slice(5, 7));
  const atMin = view.year === todayY && view.month === todayM;
  const atMax = view.year >= MAX_YEAR && view.month === 12;

  const firstWeekday = (new Date(Date.UTC(view.year, view.month - 1, 1)).getUTCDay() + 6) % 7; // lunes = 0
  const daysInMonth = new Date(Date.UTC(view.year, view.month, 0)).getUTCDate();
  const cells = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => toISO(view.year, view.month, i + 1))
  ];

  const openPicker = (which) => {
    setStep(which);
    if (which === 'out' && !checkIn) setStep('in');
    setOpen(true);
  };

  return (
    <div className="drp" ref={wrapRef}>
      <div className="drp-triggers">
        <div className="bf">
          <label className="bf-label">Llegada</label>
          <button type="button" className="drp-trigger" onClick={() => openPicker('in')}>
            {prettyDate(checkIn)}
          </button>
        </div>
        <div className="bf">
          <label className="bf-label">Salida</label>
          <button type="button" className="drp-trigger" onClick={() => openPicker(checkIn ? 'out' : 'in')}>
            {prettyDate(checkOut)}
          </button>
        </div>
      </div>

      {open && (
        <div className="drp-pop" role="dialog" aria-label="Elegir fechas">
          <div className="drp-head">
            <button type="button" className="drp-nav" onClick={() => shift(-1)} disabled={atMin} aria-label="Mes anterior">‹</button>
            <strong>{MONTHS[view.month - 1]} {view.year}</strong>
            <button type="button" className="drp-nav" onClick={() => shift(1)} disabled={atMax} aria-label="Mes siguiente">›</button>
          </div>
          <p className="drp-hint">
            {step === 'out' ? 'Ahora elige tu día de salida' : 'Elige tu día de llegada'}
            {loading && ' · cargando…'}
          </p>
          <div className="drp-grid">
            {WEEKDAYS.map((w) => <span key={w} className="drp-wd">{w}</span>)}
            {cells.map((iso, i) => {
              if (!iso) return <span key={`e${i}`} />;
              const past = iso < today;
              const full = !!soldOut[iso];
              const selStart = iso === checkIn;
              const selEnd = iso === checkOut;
              const inRange = checkIn && checkOut && iso > checkIn && iso < checkOut;
              // En el paso "salida", un día agotado todavía puede ser salida (la noche de ese día no se ocupa)
              const disabled = past || (step === 'in' || !checkIn
                ? full
                : iso > checkIn ? !canCheckOut(checkIn, iso) : full);
              const cls = ['drp-day'];
              if (full) cls.push('is-full');
              if (isPeak(iso) && !full) cls.push('is-peak');
              if (selStart || selEnd) cls.push('is-sel');
              if (inRange) cls.push('is-range');
              return (
                <button
                  key={iso}
                  type="button"
                  className={cls.join(' ')}
                  disabled={disabled}
                  onClick={() => pick(iso)}
                  title={full ? 'Sin disponibilidad' : isPeak(iso) ? 'Temporada alta' : undefined}
                >
                  {Number(iso.slice(8))}
                </button>
              );
            })}
          </div>
          <div className="drp-legend">
            <span><i className="drp-dot dot-full" /> Agotado</span>
            <span><i className="drp-dot dot-peak" /> Temporada alta</span>
          </div>
        </div>
      )}
    </div>
  );
}
