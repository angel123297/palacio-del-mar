import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import { formatCalendarDate } from '../utils/format';

const localTodayUTC = () => {
  const t = new Date();
  return Date.UTC(t.getFullYear(), t.getMonth(), t.getDate());
};

const whenLabel = (checkIn) => {
  const [y, m, d] = String(checkIn).slice(0, 10).split('-').map(Number);
  const days = Math.round((Date.UTC(y, m - 1, d) - localTodayUTC()) / 86400000);
  if (days <= 0) return 'Llegas hoy';
  if (days === 1) return 'Llegas mañana';
  return `Llegas en ${days} días`;
};

/** Próximas llegadas del huésped (GET /bookings/upcoming). reloadKey fuerza recarga. */
export default function UpcomingBookings({ reloadKey }) {
  const [list, setList] = useState([]);

  useEffect(() => {
    api.get('/bookings/upcoming', { params: { days: 90 } })
      .then((res) => setList(res.data.data || []))
      .catch(() => setList([]));
  }, [reloadKey]);

  if (list.length === 0) return null;

  return (
    <div className="upcoming-strip">
      <p className="sec-label">Próximas llegadas</p>
      <div className="upcoming-list">
        {list.map((b) => {
          const bId = b._id || b.id;
          return (
            <Link key={bId} className="upcoming-card" to={`/reservas/${bId}`}>
              <strong>{b.suite?.name || 'Suite'}</strong>
              <span>{formatCalendarDate(b.checkIn)} → {formatCalendarDate(b.checkOut)}</span>
              <span className="upcoming-when">{whenLabel(b.checkIn)}</span>
            </Link>
          );
        })}
      </div>
    </div>
  );
}
