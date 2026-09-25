import { useState } from 'react';
import { useBookingCart, todayISO } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';

export default function BookingBar() {
  const { search, setSearch, searchAvailability, searching } = useBookingCart();
  const toast = useToast();
  const [local, setLocal] = useState(search);

  const submit = async (e) => {
    e.preventDefault();

    if (local.checkOut <= local.checkIn) {
      toast.error('La fecha de salida debe ser posterior a la de entrada');
      return;
    }

    setSearch(local);
    try {
      const data = await searchAvailability(local);
      document.querySelector('#rooms')?.scrollIntoView({ behavior: 'smooth' });
      if (data.stats.availableSuites === 0) {
        toast.info('No hay suites disponibles para esas fechas. Te mostramos fechas alternativas si las hay.');
      } else {
        toast.success(`${data.stats.availableSuites} suite(s) disponibles para tus fechas`);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo consultar la disponibilidad');
    }
  };

  return (
    <div id="booking-bar">
      <form className="bf-form" onSubmit={submit}>
        <div className="bf">
          <label className="bf-label">Llegada</label>
          <input
            type="date"
            required
            min={todayISO()}
            value={local.checkIn}
            onChange={(e) => setLocal({ ...local, checkIn: e.target.value })}
          />
        </div>
        <div className="bf">
          <label className="bf-label">Salida</label>
          <input
            type="date"
            required
            min={local.checkIn}
            value={local.checkOut}
            onChange={(e) => setLocal({ ...local, checkOut: e.target.value })}
          />
        </div>
        <div className="bf">
          <label className="bf-label">Huéspedes</label>
          <select
            value={local.guests}
            onChange={(e) => setLocal({ ...local, guests: Number(e.target.value) })}
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <option key={n} value={n}>{n} {n === 1 ? 'huésped' : 'huéspedes'}</option>
            ))}
          </select>
        </div>
        <div className="bf bf-submit">
          <button className="btn-primary bf-submit-btn" type="submit" disabled={searching}>
            {searching ? 'Buscando…' : 'Ver disponibilidad'}
          </button>
        </div>
      </form>
    </div>
  );
}
