import { useEffect, useState } from 'react';

export default function PromoBanner() {
  const [visible, setVisible] = useState(true);
  const [time, setTime] = useState(() => {
    const target = new Date();
    target.setDate(target.getDate() + 3);
    return target;
  });
  const [left, setLeft] = useState({ h: 0, m: 0, s: 0 });

  useEffect(() => {
    const tick = () => {
      const diff = Math.max(0, time - new Date());
      const h = Math.floor(diff / 3_600_000);
      const m = Math.floor((diff % 3_600_000) / 60_000);
      const s = Math.floor((diff % 60_000) / 1000);
      setLeft({ h, m, s });
    };
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [time]);

  if (!visible) return null;

  const pad = (n) => String(n).padStart(2, '0');

  return (
    <div id="promo-banner">
      <span className="promo-text">
        <strong>Reserva directa:</strong> Ahorra hasta un 20% · Desayuno incluido · Cancelación gratuita
      </span>
      <div id="banner-countdown">
        <span className="bcd">{pad(left.h)}</span>:
        <span className="bcd">{pad(left.m)}</span>:
        <span className="bcd">{pad(left.s)}</span>
      </div>
      <button id="promo-close-btn" onClick={() => setVisible(false)} aria-label="Cerrar">×</button>
    </div>
  );
}
