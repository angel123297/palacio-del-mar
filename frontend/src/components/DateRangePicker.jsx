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
const shortDate = (iso) => {
  const [, m, d] = iso.split('-').map(Number);
  return `${d} ${MONTHS[m - 1].slice(0, 3).toLowerCase()}`;
};
const shortRange = (a, b) => (a === b ? shortDate(a) : `${shortDate(a)} – ${shortDate(b)}`);

/**
 * Selector de rango de fechas que muestra los días sin disponibilidad
 * (GET /availability/monthly), la temporada alta y los festivos
 * (GET /availability/peak-dates) y los descuentos vigentes
 * (GET /availability/promotions).
 * El servidor sigue siendo quien valida la reserva: esto solo orienta al huésped.
 */
export default function DateRangePicker({ checkIn, checkOut, guests, branch = '', onChange }) {
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
  const [holidays, setHolidays] = useState([]); // [{ name, date }]
  const [peakYears, setPeakYears] = useState({});
  const [promoMonths, setPromoMonths] = useState({}); // { 'YYYY-M': [promoción, ...] }
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

  // Los datos dependen del nº de huéspedes (suites con capacidad suficiente) y de la sucursal
  useEffect(() => { setSoldOut({}); setLoaded({}); setPromoMonths({}); }, [guests, branch]);

  const loadMonth = useCallback(async (year, month) => {
    const key = `${guests}:${branch}:${year}-${month}`;
    if (loaded[key]) return;
    setLoading(true);
    try {
      const res = await api.get('/availability/monthly', { params: { year, month, guests, ...(branch ? { branch } : {}) } });
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
  }, [guests, branch, loaded]);

  const loadPeak = useCallback(async (year) => {
    if (peakYears[year]) return;
    setPeakYears((p) => ({ ...p, [year]: true }));
    try {
      const res = await api.get('/availability/peak-dates', { params: { year } });
      const d = res.data?.data || {};
      const ranges = [...(d.highSeason || []), ...(d.peakSeason || [])];
      setPeakRanges((prev) => [...prev, ...ranges]);
      setHolidays((prev) => [...prev, ...(d.holidays || [])]);
    } catch { /* decorativo: si falla, no se marca temporada alta ni festivos */ }
  }, [peakYears]);

  // Descuentos del mes (promociones activas de las sucursales)
  const loadPromos = useCallback(async (year, month) => {
    const key = `${year}-${month}`;
    if (promoMonths[key]) return;
    setPromoMonths((p) => ({ ...p, [key]: [] }));
    try {
      const res = await api.get('/availability/promotions', { params: { year, month, ...(branch ? { branch } : {}) } });
      setPromoMonths((p) => ({ ...p, [key]: res.data?.data?.promotions || [] }));
    } catch { /* informativo: si falla, simplemente no se muestran descuentos */ }
  }, [branch, promoMonths]);

  useEffect(() => {
    if (!open) return;
    loadMonth(view.year, view.month);
    loadPromos(view.year, view.month);
    // Navidad/Año Nuevo cruza de año: se pide también el año anterior
    loadPeak(view.year);
    loadPeak(view.year - 1);
  }, [open, view, loadMonth, loadPeak, loadPromos]);

  const isPeak = (iso) => peakRanges.some((r) => iso >= r.start && iso <= r.end);

  // Descuentos por día (YYYY-MM-DD -> promociones que lo cubren)
  const promosByDay = useMemo(() => {
    const map = {};
    const seen = new Set();
    Object.values(promoMonths).flat().forEach((p) => {
      if (seen.has(p.id)) return;
      seen.add(p.id);
      for (let d = p.startDate; d <= p.endDate; d = addDays(d, 1)) {
        (map[d] = map[d] || []).push(p);
      }
    });
    return map;
  }, [branch, promoMonths]);

  const holidayByDay = useMemo(() => {
    const map = {};
    holidays.forEach((h) => { map[h.date] = h.name; });
    return map;
  }, [holidays]);

  // Lista "En este mes": descuentos, temporadas y festivos
  const monthItems = useMemo(() => {
    const first = toISO(view.year, view.month, 1);
    const last = toISO(view.year, view.month, new Date(Date.UTC(view.year, view.month, 0)).getUTCDate());
    const items = [];
    const seen = new Set();
    (promoMonths[`${view.year}-${view.month}`] || [])
      .slice()
      .sort((a, b) => b.discountPercent - a.discountPercent)
      .forEach((p) => items.push({
        key: `p${p.id}`, kind: 'promo', name: p.title,
        sub: p.branch?.name, when: shortRange(p.startDate, p.endDate), badge: `-${p.discountPercent}%`
      }));
    peakRanges
      .filter((r) => r.start <= last && r.end >= first)
      .forEach((r) => {
        const k = `s${r.name}${r.start}`;
        if (seen.has(k)) return;
        seen.add(k);
        items.push({ key: k, kind: 'season', name: r.name, sub: 'Temporada alta', when: shortRange(r.start, r.end) });
      });
    holidays
      .filter((h) => h.date >= first && h.date <= last)
      .forEach((h) => {
        const k = `h${h.date}`;
        if (seen.has(k)) return;
        seen.add(k);
        items.push({ key: k, kind: 'holiday', name: h.name, sub: 'Festivo', when: shortDate(h.date) });
      });
    return items;
  }, [view, promoMonths, peakRanges, holidays]);

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

  // Texto del globo informativo de cada día
  const dayTitle = (iso, full) => {
    const lines = [];
    if (full) lines.push('Sin disponibilidad');
    (promosByDay[iso] || []).forEach((p) => lines.push(`${p.discountPercent}% de descuento · ${p.title}${p.branch?.name ? ` (${p.branch.name})` : ''}`));
    if (holidayByDay[iso]) lines.push(holidayByDay[iso]);
    if (!full && isPeak(iso)) lines.push('Temporada alta');
    return lines.length ? lines.join('\n') : undefined;
  };

  return (
    <div className="drp" ref={wrapRef}>
      <div className="drp-triggers">
        <div className="bf">
          <label className="bf-label">Llegada</label>
          <button type="button" className={`drp-trigger ${open && step === 'in' ? 'is-active' : ''}`} onClick={() => openPicker('in')}>
            {prettyDate(checkIn)}
          </button>
        </div>
        <div className="bf">
          <label className="bf-label">Salida</label>
          <button type="button" className={`drp-trigger ${open && step === 'out' ? 'is-active' : ''}`} onClick={() => openPicker(checkIn ? 'out' : 'in')}>
            {prettyDate(checkOut)}
          </button>
        </div>
      </div>

      {open && (
        <div className="drp-pop" role="dialog" aria-label="Elegir fechas">
          <svg className="drp-crown" viewBox="0 0 220 24" aria-hidden="true">
            <defs>
              <linearGradient id="drpLineL" gradientUnits="userSpaceOnUse" x1="14" y1="0" x2="84" y2="0">
                <stop offset="0" stopColor="#c9a96e" stopOpacity="0" />
                <stop offset="1" stopColor="#e8d5a3" stopOpacity="0.95" />
              </linearGradient>
              <linearGradient id="drpLineR" gradientUnits="userSpaceOnUse" x1="206" y1="0" x2="136" y2="0">
                <stop offset="0" stopColor="#c9a96e" stopOpacity="0" />
                <stop offset="1" stopColor="#e8d5a3" stopOpacity="0.95" />
              </linearGradient>
            </defs>
            <path d="M14 11.5H84" stroke="url(#drpLineL)" strokeWidth="1" />
            <path d="M206 11.5H136" stroke="url(#drpLineR)" strokeWidth="1" />
            <path d="M92 11.5 95 8.5 98 11.5 95 14.5Z" fill="#c9a96e" />
            <path d="M128 11.5 125 8.5 122 11.5 125 14.5Z" fill="#c9a96e" />
            <path d="M110 3 119 12 110 21 101 12Z" fill="#0a0805" stroke="#e8d5a3" strokeWidth="1.2" />
            <path d="M110 8 114 12 110 16 106 12Z" fill="#f4ecd8" />
          </svg>
          <div className="drp-head">
            <button type="button" className="drp-nav" onClick={() => shift(-1)} disabled={atMin} aria-label="Mes anterior">
              <svg className="drp-arrow" viewBox="0 0 28 14" aria-hidden="true"><path d="M27 7H5M9.5 2.5 4 7l5.5 4.5" /><circle cx="25" cy="7" r="1.5" /></svg>
            </button>
            <strong>{MONTHS[view.month - 1]} {view.year}</strong>
            <button type="button" className="drp-nav" onClick={() => shift(1)} disabled={atMax} aria-label="Mes siguiente">
              <svg className="drp-arrow is-next" viewBox="0 0 28 14" aria-hidden="true"><path d="M27 7H5M9.5 2.5 4 7l5.5 4.5" /><circle cx="25" cy="7" r="1.5" /></svg>
            </button>
          </div>
          <div className="drp-divider" aria-hidden="true"><i /><span /><i /></div>
          <p className="drp-hint">
            {step === 'out' ? 'Ahora elige tu día de salida' : 'Elige tu día de llegada'}
            {loading && ' · cargando…'}
          </p>
          <div className="drp-grid" key={`${view.year}-${view.month}`}>
            {WEEKDAYS.map((w) => <span key={w} className="drp-wd">{w}</span>)}
            {cells.map((iso, i) => {
              if (!iso) return <span key={`e${i}`} />;
              const past = iso < today;
              const full = !!soldOut[iso];
              const selStart = iso === checkIn;
              const selEnd = iso === checkOut;
              const inRange = checkIn && checkOut && iso > checkIn && iso < checkOut;
              const hasPromo = !!promosByDay[iso];
              const hasHoliday = !!holidayByDay[iso];
              // En el paso "salida", un día agotado todavía puede ser salida (la noche de ese día no se ocupa)
              const disabled = past || (step === 'in' || !checkIn
                ? full
                : iso > checkIn ? !canCheckOut(checkIn, iso) : full);
              const cls = ['drp-day'];
              if (full) cls.push('is-full');
              if (isPeak(iso) && !full) cls.push('is-peak');
              if (selStart || selEnd) cls.push('is-sel');
              if (inRange) cls.push('is-range');
              if (selStart && checkOut) cls.push('is-start');
              if (selEnd) cls.push('is-end');
              return (
                <button
                  key={iso}
                  type="button"
                  className={cls.join(' ')}
                  disabled={disabled}
                  onClick={() => pick(iso)}
                  title={dayTitle(iso, full)}
                >
                  {Number(iso.slice(8))}
                  {hasPromo && !past && <i className="drp-mark mk-promo" aria-hidden="true" />}
                  {hasHoliday && !past && <i className="drp-mark mk-holiday" aria-hidden="true" />}
                </button>
              );
            })}
          </div>

          {monthItems.length > 0 && (
            <div className="drp-info">
              <p className="drp-info-title">En {MONTHS[view.month - 1]}</p>
              <ul className="drp-info-list">
                {monthItems.map((it) => (
                  <li key={it.key} className={`drp-info-item is-${it.kind}`}>
                    <i className="drp-info-mark" aria-hidden="true" />
                    <span className="drp-info-name">
                      {it.name}
                      {it.sub && <small>{it.sub}</small>}
                    </span>
                    <span className="drp-info-when">{it.when}</span>
                    {it.badge && <b className="drp-info-badge">{it.badge}</b>}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <div className="drp-legend">
            <span><i className="drp-dot dot-full" /> Agotado</span>
            <span><i className="drp-dot dot-peak" /> Temporada alta</span>
            <span><i className="drp-dot dot-promo" /> Descuento</span>
            <span><i className="drp-dot dot-holiday" /> Festivo</span>
          </div>
        </div>
      )}
    </div>
  );
}
