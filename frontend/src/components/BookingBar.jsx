import { useEffect, useState } from 'react';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import DateRangePicker from './DateRangePicker.jsx';

export default function BookingBar() {
  const { search, setSearch, searchAvailability, searching, branch, setBranch, branches } = useBookingCart();
  const toast = useToast();
  const [local, setLocal] = useState(search);

  // Si otra parte de la página cambia la búsqueda (p. ej. fechas alternativas), la barra la refleja
  useEffect(() => { setLocal(search); }, [search]);

  const children = local.children || 0;
  const adults = Math.max(1, local.guests - children);

  const submit = async (e) => {
    e.preventDefault();

    if (local.checkOut <= local.checkIn) {
      toast.error('La fecha de salida debe ser posterior a la de entrada');
      return;
    }

    setSearch(local);
    try {
      const { children: _c, ...params } = local; // la disponibilidad solo necesita el total de huéspedes
      const data = await searchAvailability(params);
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
        <DateRangePicker
          checkIn={local.checkIn}
          checkOut={local.checkOut}
          guests={local.guests}
          branch={branch}
          onChange={({ checkIn, checkOut }) => setLocal({ ...local, checkIn, checkOut })}
        />
        {branches.length > 1 && (
          <div className="bf">
            <label className="bf-label" htmlFor="bf-branch">Sucursal</label>
            <select id="bf-branch" value={branch} onChange={(e) => setBranch(e.target.value)}>
              <option value="">Todas las sucursales</option>
              {branches.map((b) => (
                <option key={b.slug} value={b.slug}>{b.name.replace(/^Palacio del Mar\s*·\s*/, '')}</option>
              ))}
            </select>
          </div>
        )}
        <div className="bf">
          <label className="bf-label" htmlFor="bf-adults">Adultos</label>
          <select
            id="bf-adults"
            value={adults}
            onChange={(e) => setLocal({ ...local, guests: Number(e.target.value) + children })}
          >
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <option key={n} value={n}>{n} {n === 1 ? 'adulto' : 'adultos'}</option>
            ))}
          </select>
        </div>
        <div className="bf">
          <label className="bf-label" htmlFor="bf-children">Niños</label>
          <select
            id="bf-children"
            value={children}
            onChange={(e) => setLocal({ ...local, children: Number(e.target.value), guests: adults + Number(e.target.value) })}
          >
            {[0, 1, 2, 3, 4].map((n) => (
              <option key={n} value={n}>{n} {n === 1 ? 'niño' : 'niños'}</option>
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
