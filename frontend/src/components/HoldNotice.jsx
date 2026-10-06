import { useEffect, useState } from 'react';

// Cuenta regresiva de la retención de una reserva pendiente. La hora límite
// viene del servidor (holdExpiresAt); aquí solo se muestra el tiempo restante.
const format = (ms) => {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
};

export default function HoldNotice({ booking }) {
  const deadline = booking?.holdExpiresAt ? new Date(booking.holdExpiresAt).getTime() : null;
  const active = booking?.status === 'pending' && deadline;
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!active) return undefined;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [active]);

  if (booking?.status === 'expired') {
    return <p className="hold-notice hold-expired">Esta reserva venció porque no se pagó a tiempo. La habitación quedó libre.</p>;
  }
  if (!active) return null;

  const left = deadline - now;
  if (left <= 0) {
    return <p className="hold-notice hold-expired">El tiempo de retención terminó; la habitación se liberará en instantes.</p>;
  }
  return (
    <p className={`hold-notice ${left < 5 * 60000 ? 'hold-urgent' : ''}`}>
      Te guardamos la habitación durante <strong>{format(left)}</strong>. Si no se paga a tiempo, la reserva vence y se libera.
    </p>
  );
}
