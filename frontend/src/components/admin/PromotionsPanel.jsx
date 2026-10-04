import { useCallback, useEffect, useState } from 'react';
import api from '../../api/client';
import { useToast } from '../../context/ToastContext.jsx';
import { formatDate } from '../../utils/format';

const STATE_LABELS = { current: 'Vigente', upcoming: 'Próxima', past: 'Terminada', paused: 'Pausada' };
const STATE_PILL = { current: 'pill-ok', upcoming: 'pill-pending', past: 'pill-cancelled', paused: 'pill-cancelled' };
const FILTERS = [
  { id: 'all', label: 'Todas' }, { id: 'current', label: 'Vigentes' }, { id: 'upcoming', label: 'Próximas' },
  { id: 'past', label: 'Terminadas' }, { id: 'paused', label: 'Pausadas' }
];
const EMPTY = { branch: '', title: '', discountPercent: '', startDate: '', endDate: '' };
const day = (value) => String(value).slice(0, 10);

export default function PromotionsPanel() {
  const [promos, setPromos] = useState(null);
  const [branches, setBranches] = useState([]);
  const [state, setState] = useState('all');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [warnings, setWarnings] = useState([]);
  const toast = useToast();

  const load = useCallback(() => {
    api.get('/admin/promotions', { params: { state } })
      .then((res) => setPromos(res.data.data.promotions))
      .catch(() => toast.error('No se pudieron cargar las promociones'));
  }, [state]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load]);

  useEffect(() => {
    api.get('/branches').then((res) => setBranches(res.data.data || [])).catch(() => {});
  }, []);

  const reset = () => { setForm(EMPTY); setEditingId(null); };

  const edit = (p) => {
    setEditingId(p._id);
    setWarnings([]);
    setForm({
      branch: p.branch?._id || '', title: p.title, discountPercent: p.discountPercent,
      startDate: day(p.startDate), endDate: day(p.endDate)
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { title: form.title, discountPercent: Number(form.discountPercent), startDate: form.startDate, endDate: form.endDate };
      const res = editingId
        ? await api.put(`/admin/promotions/${editingId}`, body)
        : await api.post('/admin/promotions', { ...body, branch: form.branch });
      toast.success(res.data.message);
      setWarnings(res.data.data.warnings || []);
      reset();
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar la promoción');
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (p) => {
    try {
      const res = await api.put(`/admin/promotions/${p._id}`, { active: !p.active });
      setWarnings(res.data.data.warnings || []);
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo actualizar');
    }
  };

  const remove = async (p) => {
    if (!window.confirm(`¿Eliminar la promoción "${p.title}"? Las reservas ya hechas conservan su precio.`)) return;
    try {
      await api.delete(`/admin/promotions/${p._id}`);
      toast.success('Promoción eliminada');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar');
    }
  };

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <div className="admin-tab">
      <form className="admin-form" onSubmit={submit}>
        <h3>{editingId ? 'Editar promoción' : 'Nueva promoción'}</h3>
        <p className="form-hint">
          Una promoción activa rebaja el precio de las reservas <strong>nuevas</strong> que toquen sus noches.
          Las reservas ya hechas conservan el precio que se les guardó. Si dos se cruzan, se aplica la de mayor descuento.
        </p>
        <div className="admin-form-grid">
          <select required disabled={!!editingId} value={form.branch} onChange={set('branch')}>
            <option value="">Sucursal…</option>
            {branches.map((b) => <option key={b._id} value={b._id}>{b.name}</option>)}
          </select>
          <input placeholder="Título (ej. Escápate a Getsemaní)" required maxLength={80} value={form.title} onChange={set('title')} />
          <input placeholder="Descuento % (1 a 60)" type="number" min="1" max="60" step="1" required value={form.discountPercent} onChange={set('discountPercent')} />
          <label className="field-label">Primera noche
            <input type="date" required value={form.startDate} onChange={set('startDate')} />
          </label>
          <label className="field-label">Última noche (incluida)
            <input type="date" required min={form.startDate || undefined} value={form.endDate} onChange={set('endDate')} />
          </label>
        </div>
        <div className="admin-form-actions">
          <button className="btn-primary" type="submit" disabled={busy}>{editingId ? 'Guardar cambios' : 'Crear promoción'}</button>
          {editingId && <button type="button" className="link-btn" onClick={reset}>Cancelar edición</button>}
        </div>
        {warnings.map((w) => <p key={w} className="form-hint promo-warning">⚠ {w}</p>)}
      </form>

      <div className="admin-views">
        {FILTERS.map((f) => (
          <button key={f.id} className={`filter-chip ${state === f.id ? 'active' : ''}`} onClick={() => { setState(f.id); setPromos(null); }}>
            {f.label}
          </button>
        ))}
      </div>

      <div className="admin-table-wrap">
        {promos === null && <div className="spinner" />}
        {promos && (
          <table className="admin-table">
            <thead><tr><th>Sucursal</th><th>Promoción</th><th>Descuento</th><th>Noches cubiertas</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              {promos.map((p) => (
                <tr key={p._id}>
                  <td>{p.branch?.name || '—'}</td>
                  <td>
                    {p.title}
                    {p.isSample && <><br /><span className="form-hint">Dato de ejemplo</span></>}
                  </td>
                  <td>−{p.discountPercent}%</td>
                  <td>{formatDate(p.startDate)} → {formatDate(p.endDate)}</td>
                  <td><span className={`pill ${STATE_PILL[p.state]}`}>{STATE_LABELS[p.state]}</span></td>
                  <td>
                    <div className="admin-actions-cell">
                    <button className="link-btn" onClick={() => edit(p)}>Editar</button>
                    <button className="link-btn" onClick={() => toggle(p)}>{p.active ? 'Pausar' : 'Activar'}</button>
                    <button className="link-btn danger" onClick={() => remove(p)}>Eliminar</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {promos && promos.length === 0 && <p className="form-hint">No hay promociones en esta vista.</p>}
      </div>
    </div>
  );
}
