import { useCallback, useEffect, useState } from 'react';
import api from '../../api/client';
import { useToast } from '../../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../../utils/format';

// Vistas como las pestañas de "Reservas" de Booking.com
const VIEWS = [
  { id: 'all', label: 'Todas' },
  { id: 'arrivals', label: 'Llegadas hoy', count: 'arrivals' },
  { id: 'departures', label: 'Salidas hoy', count: 'departures' },
  { id: 'inhouse', label: 'En el hotel', count: 'inhouse' },
  { id: 'upcoming', label: 'Próximas' },
  { id: 'pending', label: 'Pendientes de pago', count: 'pending' },
  { id: 'cancelled', label: 'Canceladas / no-show' },
  { id: 'expired', label: 'Vencidas' }
];

const STATUS_LABELS = {
  pending: 'Pendiente', confirmed: 'Confirmada', completed: 'Completada',
  cancelled: 'Cancelada', no_show: 'No se presentó', expired: 'Vencida'
};
const PAYMENT_LABELS = { pending: 'Pendiente', partial: 'Parcial', paid: 'Pagado', failed: 'Fallido', refunded: 'Reembolsado' };
const SEASON_LABELS = { low: 'Baja', mid: 'Media', high: 'Alta', peak: 'Pico' };
const DISCOUNT_LABELS = { promotion: 'Promoción', long_stay: 'Estadía larga' };

const statusPill = (s) =>
  ['confirmed', 'completed'].includes(s) ? 'pill-ok'
    : ['cancelled', 'expired', 'no_show'].includes(s) ? 'pill-cancelled'
      : 'pill-pending';

const nightsOf = (b) => Math.round((new Date(b.checkOut) - new Date(b.checkIn)) / 86400000);

export default function BookingsPanel() {
  const [view, setView] = useState('all');
  const [searchText, setSearchText] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const toast = useToast();

  const load = useCallback(() => {
    api.get('/admin/bookings', { params: { view, search: search || undefined, page } })
      .then((res) => setData(res.data.data))
      .catch(() => toast.error('No se pudieron cargar las reservas'));
  }, [view, search, page]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  const changeView = (id) => { setView(id); setPage(1); setData(null); };
  const submitSearch = (e) => { e.preventDefault(); setSearch(searchText.trim()); setPage(1); setData(null); };

  const changeStatus = async (b, status) => {
    let reason;
    if (status === 'cancelled') {
      reason = window.prompt(`Cancelar la reserva de ${b.guestName}. Se devolverá todo lo cobrado.\nMotivo (opcional):`);
      if (reason === null) return; // el administrador se arrepintió
    } else if (!window.confirm(`¿Marcar la reserva de ${b.guestName} como "${STATUS_LABELS[status]}"?`)) {
      return;
    }
    try {
      await api.put(`/admin/bookings/${b._id}/status`, { status, reason: reason || undefined });
      toast.success('Reserva actualizada');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo actualizar la reserva');
    }
  };

  const updatePayment = async (id, paymentStatus) => {
    try {
      const body = { paymentStatus };
      if (paymentStatus === 'partial') {
        const raw = window.prompt('Importe cobrado hasta ahora (menor que el total):');
        const amount = Number(raw);
        if (!raw || !Number.isFinite(amount) || amount <= 0) { toast.error('Importe inválido'); return; }
        body.amount = amount;
      }
      await api.put(`/bookings/admin/${id}/payment-status`, body);
      toast.success('Estado de pago actualizado');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo actualizar');
    }
  };

  const actionsFor = (b) => {
    const futureStay = data && b.checkIn.slice(0, 10) > data.today;
    const acts = [];
    if (b.status === 'pending') acts.push({ status: 'confirmed', label: 'Confirmar' });
    if (b.status === 'confirmed') {
      acts.push({ status: 'completed', label: 'Check-out', disabled: futureStay, hint: 'Solo desde el día de llegada' });
      acts.push({ status: 'no_show', label: 'No-show', disabled: futureStay, hint: 'Solo desde el día de llegada' });
    }
    if (['pending', 'confirmed'].includes(b.status)) acts.push({ status: 'cancelled', label: 'Cancelar', danger: true });
    return acts;
  };

  const bookings = data?.bookings;
  const pg = data?.pagination;

  return (
    <div className="admin-tab">
      <div className="admin-views">
        {VIEWS.map((v) => (
          <button key={v.id} className={`filter-chip ${view === v.id ? 'active' : ''}`} onClick={() => changeView(v.id)}>
            {v.label}
            {v.count && data && <span className="chip-count">{data.summary[v.count]}</span>}
          </button>
        ))}
      </div>

      <form className="admin-filters admin-search" onSubmit={submitSearch}>
        <input
          type="search"
          placeholder="Buscar por huésped, correo, teléfono o número de reserva"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        <button className="btn-primary" type="submit">Buscar</button>
        {search && (
          <button type="button" className="link-btn" onClick={() => { setSearchText(''); setSearch(''); setPage(1); setData(null); }}>
            Quitar búsqueda
          </button>
        )}
      </form>

      <div className="admin-table-wrap">
        {data === null && <div className="spinner" />}
        {bookings && (
          <table className="admin-table">
            <thead>
              <tr><th>Huésped</th><th>Suite</th><th>Estancia</th><th>Total</th><th>Estado</th><th>Pago</th><th>Acciones</th></tr>
            </thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b._id}>
                  <td>
                    {b.guestName}
                    <br /><span className="form-hint">{b.guestEmail}</span>
                    {b.guestPhone && <><br /><span className="form-hint">{b.guestPhone}</span></>}
                  </td>
                  <td>
                    {b.suite?.name || 'Suite eliminada'}
                    {b.branch?.name && <><br /><span className="form-hint">{b.branch.name}</span></>}
                  </td>
                  <td>
                    {formatDate(b.checkIn)} → {formatDate(b.checkOut)}
                    <br /><span className="form-hint">{nightsOf(b)} {nightsOf(b) === 1 ? 'noche' : 'noches'}</span>
                  </td>
                  <td>
                    {formatCOP(b.totalPrice)}
                    {b.pricing?.discountType && (
                      <><br /><span className="pill pill-ok">{DISCOUNT_LABELS[b.pricing.discountType] || b.pricing.discountType}</span></>
                    )}
                    {b.discountReason && <><br /><span className="form-hint">{b.discountReason}</span></>}
                    {b.pricing?.nights?.length > 0 && (
                      <details className="price-detail">
                        <summary>Ver desglose por noche</summary>
                        <ul>
                          {b.pricing.nights.map((n) => (
                            <li key={n.date}>
                              {formatDate(n.date)} · {SEASON_LABELS[n.season] || n.season} · {formatCOP(n.price)}
                              {n.promoPercent > 0 ? ` · −${n.promoPercent}% ${n.promotionTitle || ''}` : ''}
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                  </td>
                  <td>
                    <span className={`pill ${statusPill(b.status)}`}>{STATUS_LABELS[b.status] || b.status}</span>
                    {b.status === 'cancelled' && b.cancellationDetails?.refundStatus === 'pending' && (
                      <><br /><span className="form-hint">Reembolso pendiente: {formatCOP(b.cancellationDetails.refundAmount)}</span></>
                    )}
                  </td>
                  <td>
                    <select
                      value={b.paymentStatus}
                      disabled={b.status === 'expired'}
                      title={b.status === 'expired' ? 'Reserva vencida: no se puede cobrar. Crea una reserva nueva.' : undefined}
                      onChange={(e) => updatePayment(b._id, e.target.value)}
                    >
                      {Object.entries(PAYMENT_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                    </select>
                  </td>
                  <td>
                    <div className="admin-actions-cell">
                    {actionsFor(b).map((a) => (
                      <button
                        key={a.status}
                        className={`link-btn ${a.danger ? 'danger' : ''}`}
                        disabled={a.disabled}
                        title={a.disabled ? a.hint : undefined}
                        onClick={() => changeStatus(b, a.status)}
                      >
                        {a.label}
                      </button>
                    ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {bookings && bookings.length === 0 && <p className="form-hint">No hay reservas en esta vista.</p>}
      </div>

      {pg && pg.totalPages > 1 && (
        <div className="admin-pager">
          <button className="link-btn" disabled={page <= 1} onClick={() => { setPage(page - 1); setData(null); }}>← Anterior</button>
          <span className="muted">Página {pg.currentPage} de {pg.totalPages} · {pg.totalItems} reservas</span>
          <button className="link-btn" disabled={page >= pg.totalPages} onClick={() => { setPage(page + 1); setData(null); }}>Siguiente →</button>
        </div>
      )}
    </div>
  );
}
