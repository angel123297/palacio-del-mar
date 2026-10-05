import { useEffect, useMemo, useState } from 'react';
import api from '../api/client';
import { pickPromotion } from '../utils/promos.js';

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const short = (iso) => { const [, m, d] = iso.split('-').map(Number); return `${d} ${MONTHS[m - 1]}`; };
const shortBranch = (name = '') => name.replace(/^Palacio del Mar\s*·\s*/, '');
const pad = (n) => String(n).padStart(2, '0');
const localISO = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

/**
 * Banner con la promoción REAL de las sucursales (GET /availability/promotions).
 * Sin promociones, no se muestra. La cuenta regresiva sólo aparece en las últimas 72 h.
 */
export default function PromoBanner() {
  const [visible, setVisible] = useState(true);
  const [promos, setPromos] = useState([]);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const d = new Date();
    const months = [0, 1].map((i) => new Date(d.getFullYear(), d.getMonth() + i, 1));
    Promise.all(months.map((m) =>
      api.get('/availability/promotions', { params: { year: m.getFullYear(), month: m.getMonth() + 1 } })
        .then((r) => r.data?.data?.promotions || []).catch(() => [])))
      .then((lists) => {
        const byId = new Map(lists.flat().map((p) => [p.id, p]));
        setPromos([...byId.values()]);
      });
  }, []);

  const picked = useMemo(() => pickPromotion(promos, localISO(now)), [promos, now]);
  const end = picked?.live ? new Date(`${picked.promo.endDate}T23:59:59-05:00`) : null;
  const diff = end ? Math.max(0, end - now) : 0;
  const showClock = !!end && diff > 0 && diff <= 72 * 3_600_000;

  useEffect(() => {
    if (!showClock) return undefined;
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, [showClock]);

  if (!visible || !picked) return null;
  const { promo, live } = picked;
  const where = promo.branch?.name ? ` en ${shortBranch(promo.branch.name)}` : '';

  return (
    <div id="promo-banner">
      <span className="promo-text">
        <strong>{promo.title}:</strong> {promo.discountPercent}% de descuento{where} ·{' '}
        {live ? `hasta el ${short(promo.endDate)}` : `desde el ${short(promo.startDate)} hasta el ${short(promo.endDate)}`}
      </span>
      {showClock && (
        <div id="banner-countdown" aria-label="Tiempo restante de la promoción">
          <span className="bcd">{pad(Math.floor(diff / 3_600_000))}</span>:
          <span className="bcd">{pad(Math.floor((diff % 3_600_000) / 60_000))}</span>:
          <span className="bcd">{pad(Math.floor((diff % 60_000) / 1000))}</span>
        </div>
      )}
      <button id="promo-close-btn" onClick={() => setVisible(false)} aria-label="Cerrar">×</button>
    </div>
  );
}
