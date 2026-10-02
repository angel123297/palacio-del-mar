import { useEffect, useState } from 'react';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';

const TABS = [
  { id: 'dashboard', label: 'Dashboard' },
  { id: 'suites', label: 'Suites' },
  { id: 'experiences', label: 'Experiencias' },
  { id: 'bookings', label: 'Reservas' },
  { id: 'users', label: 'Usuarios' }
];

// ============================================
// DASHBOARD
// ============================================
const MONTH_NAMES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const BOOKING_STATUS_LABELS = {
  pending: 'Pendientes',
  confirmed: 'Confirmadas',
  cancelled: 'Canceladas',
  completed: 'Completadas'
};

function StatCard({ label, value }) {
  return (
    <div className="stat-card">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
    </div>
  );
}

function BarList({ rows, emptyMessage }) {
  if (!rows.length) {
    return <p className="muted">{emptyMessage}</p>;
  }
  const max = Math.max(1, ...rows.map((r) => r.amount));
  return (
    <div className="bar-chart">
      {rows.map((r) => (
        <div className="bar-chart-row" key={r.key}>
          <span className="bar-chart-label">{r.label}</span>
          <div className="bar-chart-track">
            <div className="bar-chart-fill" style={{ width: `${Math.max(4, (r.amount / max) * 100)}%` }} />
          </div>
          <span className="bar-chart-value">{r.valueLabel}</span>
        </div>
      ))}
    </div>
  );
}

function DashboardAdmin() {
  const [bookingStats, setBookingStats] = useState(null);
  const [availabilityStats, setAvailabilityStats] = useState(null);
  const [suiteStats, setSuiteStats] = useState(null);
  const [experienceStats, setExperienceStats] = useState(null);
  const [failed, setFailed] = useState(false);
  const toast = useToast();

  useEffect(() => {
    Promise.all([
      api.get('/bookings/admin/stats'),
      api.get('/availability/stats'),
      api.get('/suites/stats'),
      api.get('/experiences/stats')
    ])
      .then(([b, a, s, e]) => {
        setBookingStats(b.data.data);
        setAvailabilityStats(a.data.data);
        setSuiteStats(s.data.data);
        setExperienceStats(e.data.data);
      })
      .catch(() => {
        setFailed(true);
        toast.error('No se pudieron cargar las estadísticas del panel');
      });
  }, []);

  if (failed) {
    return <div className="admin-tab"><p className="muted">No se pudieron cargar las estadísticas.</p></div>;
  }

  if (!bookingStats || !availabilityStats || !suiteStats || !experienceStats) {
    return <div className="admin-tab"><div className="spinner" /></div>;
  }

  const { general: bGeneral, monthlyStats, bySuite, byStatus } = bookingStats;
  const { general: aGeneral } = availabilityStats;

  const monthlyRows = monthlyStats.map((m) => ({
    key: `${m._id.year}-${m._id.month}`,
    label: `${MONTH_NAMES[m._id.month - 1]} ${m._id.year}`,
    amount: m.revenue || 0,
    valueLabel: formatCOP(m.revenue)
  }));

  const statusRows = byStatus.map((s) => ({
    key: s._id,
    label: BOOKING_STATUS_LABELS[s._id] || s._id,
    amount: s.count,
    valueLabel: `${s.count}`
  }));

  const suiteRows = bySuite.map((s) => ({
    key: s._id,
    label: s.suite?.name || 'Suite eliminada',
    amount: s.count,
    valueLabel: `${s.count} res. · ${formatCOP(s.revenue)}`
  }));

  const typeRows = Object.entries(suiteStats.typeDistribution || {}).map(([type, count]) => ({
    key: type,
    label: type,
    amount: count,
    valueLabel: `${count}`
  }));

  return (
    <div className="admin-tab">
      <div className="stat-grid">
        <StatCard label="Ingresos totales" value={formatCOP(bGeneral.totalRevenue)} />
        <StatCard label="Reservas totales" value={bGeneral.totalBookings} />
        <StatCard label="Valor promedio por reserva" value={formatCOP(bGeneral.avgBookingValue)} />
        <StatCard label="Ocupación actual" value={`${aGeneral.occupancyRate.toFixed(0)}%`} />
        <StatCard label="Suites en catálogo" value={suiteStats.total} />
        <StatCard label="Experiencias en catálogo" value={experienceStats.total} />
      </div>

      <div className="admin-form">
        <h3>Ingresos por mes</h3>
        <BarList rows={monthlyRows} emptyMessage="Aún no hay reservas registradas." />
      </div>

      <div className="admin-form">
        <h3>Reservas por estado</h3>
        <BarList rows={statusRows} emptyMessage="Aún no hay reservas registradas." />
      </div>

      <div className="admin-form">
        <h3>Suites más reservadas</h3>
        <BarList rows={suiteRows} emptyMessage="Aún no hay reservas registradas." />
      </div>

      <div className="admin-form">
        <h3>Catálogo de suites por tipo</h3>
        <BarList rows={typeRows} emptyMessage="No hay suites cargadas." />
      </div>
    </div>
  );
}

// ============================================
// SUITES
// ============================================
const EMPTY_SUITE = {
  name: '', type: 'Suite Deluxe', basePrice: '', originalPrice: '', size: '',
  maxGuests: 2, description: '', mainImage: '', amenities: ''
};

function SuitesAdmin() {
  const [suites, setSuites] = useState(null);
  const [form, setForm] = useState(EMPTY_SUITE);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => {
    api.get('/suites', { params: { limit: 50 } })
      .then((res) => setSuites(res.data.data))
      .catch(() => toast.error('No se pudieron cargar las suites'));
  };
  useEffect(load, []);

  const resetForm = () => { setForm(EMPTY_SUITE); setEditingId(null); };

  const edit = (s) => {
    setEditingId(s._id);
    setForm({
      name: s.name, type: s.type, basePrice: s.basePrice, originalPrice: s.originalPrice || '',
      size: s.size, maxGuests: s.maxGuests, description: s.description || '',
      mainImage: s.mainImage || '', amenities: (s.amenities || []).join(', ')
    });
  };

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    const payload = {
      ...form,
      basePrice: Number(form.basePrice),
      originalPrice: form.originalPrice ? Number(form.originalPrice) : undefined,
      size: Number(form.size),
      maxGuests: Number(form.maxGuests),
      amenities: form.amenities.split(',').map((a) => a.trim()).filter(Boolean)
    };
    try {
      if (editingId) {
        await api.put(`/suites/${editingId}`, payload);
        toast.success('Suite actualizada');
      } else {
        await api.post('/suites', payload);
        toast.success('Suite creada');
      }
      resetForm();
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar la suite');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id) => {
    if (!window.confirm('¿Eliminar (desactivar) esta suite?')) return;
    try {
      await api.delete(`/suites/${id}`);
      toast.success('Suite desactivada');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar');
    }
  };

  return (
    <div className="admin-tab">
      <form className="admin-form" onSubmit={submit}>
        <h3>{editingId ? 'Editar suite' : 'Nueva suite'}</h3>
        <div className="admin-form-grid">
          <input placeholder="Nombre" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <input placeholder="Tipo (ej. Suite Deluxe)" required value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })} />
          <input placeholder="Precio por noche (COP)" type="number" required value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} />
          <input placeholder="Precio tachado (opcional)" type="number" value={form.originalPrice} onChange={(e) => setForm({ ...form, originalPrice: e.target.value })} />
          <input placeholder="Tamaño (m²)" type="number" required value={form.size} onChange={(e) => setForm({ ...form, size: e.target.value })} />
          <input placeholder="Huéspedes máx." type="number" required value={form.maxGuests} onChange={(e) => setForm({ ...form, maxGuests: e.target.value })} />
          <input placeholder="URL de imagen" value={form.mainImage} onChange={(e) => setForm({ ...form, mainImage: e.target.value })} />
          <input placeholder="Amenidades (separadas por coma)" value={form.amenities} onChange={(e) => setForm({ ...form, amenities: e.target.value })} />
        </div>
        <textarea placeholder="Descripción" required rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="admin-form-actions">
          <button className="btn-primary" type="submit" disabled={busy}>{editingId ? 'Guardar cambios' : 'Crear suite'}</button>
          {editingId && <button type="button" className="link-btn" onClick={resetForm}>Cancelar edición</button>}
        </div>
      </form>

      <div className="admin-table-wrap">
        {suites === null && <div className="spinner" />}
        {suites && (
          <table className="admin-table">
            <thead><tr><th>Suite</th><th>Tipo</th><th>Precio</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              {suites.map((s) => (
                <tr key={s._id}>
                  <td>{s.name}</td>
                  <td>{s.type}</td>
                  <td>{formatCOP(s.basePrice)}</td>
                  <td>{s.available ? <span className="pill pill-ok">Activa</span> : <span className="pill pill-cancelled">Inactiva</span>}</td>
                  <td className="admin-row-actions">
                    <button className="link-btn" onClick={() => edit(s)}>Editar</button>
                    <button className="link-btn danger" onClick={() => remove(s._id)}>Eliminar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

// ============================================
// EXPERIENCIAS
// ============================================
const EMPTY_EXP = {
  name: '', category: 'tour', price: '', durationHours: '', description: '',
  mainImage: '', icon: '✨', location: ''
};

function ExperiencesAdmin() {
  const [list, setList] = useState(null);
  const [form, setForm] = useState(EMPTY_EXP);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const load = () => {
    api.get('/experiences', { params: { limit: 50 } })
      .then((res) => setList(res.data.data))
      .catch(() => toast.error('No se pudieron cargar las experiencias'));
  };
  useEffect(load, []);

  const resetForm = () => { setForm(EMPTY_EXP); setEditingId(null); };

  const edit = (x) => {
    setEditingId(x._id);
    setForm({
      name: x.name, category: x.category, price: x.price, durationHours: x.durationHours,
      description: x.description || '', mainImage: x.mainImage || '', icon: x.icon || '✨',
      location: x.location?.name || ''
    });
  };

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    const payload = {
      ...form,
      price: Number(form.price),
      durationHours: Number(form.durationHours),
      location: { name: form.location || 'Palacio del Mar' }
    };
    try {
      if (editingId) {
        await api.put(`/experiences/${editingId}`, payload);
        toast.success('Experiencia actualizada');
      } else {
        await api.post('/experiences', payload);
        toast.success('Experiencia creada');
      }
      resetForm();
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo guardar la experiencia');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id) => {
    if (!window.confirm('¿Eliminar (desactivar) esta experiencia?')) return;
    try {
      await api.delete(`/experiences/${id}`);
      toast.success('Experiencia desactivada');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar');
    }
  };

  return (
    <div className="admin-tab">
      <form className="admin-form" onSubmit={submit}>
        <h3>{editingId ? 'Editar experiencia' : 'Nueva experiencia'}</h3>
        <div className="admin-form-grid">
          <input placeholder="Nombre" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
            {['tour', 'spa', 'gastronomia', 'cultura', 'aventura', 'romance'].map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <input placeholder="Precio (COP)" type="number" required value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
          <input placeholder="Duración (horas)" type="number" step="0.5" required value={form.durationHours} onChange={(e) => setForm({ ...form, durationHours: e.target.value })} />
          <input placeholder="Ícono (emoji)" value={form.icon} onChange={(e) => setForm({ ...form, icon: e.target.value })} />
          <input placeholder="Lugar de encuentro" value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
          <input placeholder="URL de imagen" value={form.mainImage} onChange={(e) => setForm({ ...form, mainImage: e.target.value })} />
        </div>
        <textarea placeholder="Descripción" required rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <div className="admin-form-actions">
          <button className="btn-primary" type="submit" disabled={busy}>{editingId ? 'Guardar cambios' : 'Crear experiencia'}</button>
          {editingId && <button type="button" className="link-btn" onClick={resetForm}>Cancelar edición</button>}
        </div>
      </form>

      <div className="admin-table-wrap">
        {list === null && <div className="spinner" />}
        {list && (
          <table className="admin-table">
            <thead><tr><th>Experiencia</th><th>Categoría</th><th>Precio</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              {list.map((x) => (
                <tr key={x._id}>
                  <td>{x.icon} {x.name}</td>
                  <td>{x.category}</td>
                  <td>{formatCOP(x.price)}</td>
                  <td>{x.available ? <span className="pill pill-ok">Activa</span> : <span className="pill pill-cancelled">Inactiva</span>}</td>
                  <td className="admin-row-actions">
                    <button className="link-btn" onClick={() => edit(x)}>Editar</button>
                    <button className="link-btn danger" onClick={() => remove(x._id)}>Eliminar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

// ============================================
// RESERVAS
// ============================================
function BookingsAdmin() {
  const [bookings, setBookings] = useState(null);
  const [status, setStatus] = useState('');
  const toast = useToast();

  const load = () => {
    api.get('/bookings/admin/all', { params: status ? { status } : {} })
      .then((res) => setBookings(res.data.data.bookings))
      .catch(() => toast.error('No se pudieron cargar las reservas'));
  };
  useEffect(load, [status]);

  const updatePayment = async (id, paymentStatus) => {
    try {
      const body = { paymentStatus };
      if (paymentStatus === 'partial') {
        // Un pago parcial necesita el importe cobrado hasta ahora
        const raw = window.prompt('Importe cobrado hasta ahora (menor que el total):');
        const amount = Number(raw);
        if (!raw || !Number.isFinite(amount) || amount <= 0) {
          toast.error('Importe inválido');
          return;
        }
        body.amount = amount;
      }
      await api.put(`/bookings/admin/${id}/payment-status`, body);
      toast.success('Estado de pago actualizado');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo actualizar');
    }
  };

  return (
    <div className="admin-tab">
      <div className="admin-filters">
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">Todos los estados</option>
          {['pending', 'confirmed', 'completed', 'cancelled', 'no-show'].map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <div className="admin-table-wrap">
        {bookings === null && <div className="spinner" />}
        {bookings && (
          <table className="admin-table">
            <thead><tr><th>Huésped</th><th>Suite</th><th>Fechas</th><th>Total</th><th>Estado</th><th>Pago</th></tr></thead>
            <tbody>
              {bookings.map((b) => (
                <tr key={b._id}>
                  <td>{b.guestName}<br /><span className="form-hint">{b.guestEmail}</span></td>
                  <td>{b.suite?.name}</td>
                  <td>{formatDate(b.checkIn)} → {formatDate(b.checkOut)}</td>
                  <td>{formatCOP(b.totalPrice)}</td>
                  <td><span className="pill pill-pending">{b.statusLabel || b.status}</span></td>
                  <td>
                    <select value={b.paymentStatus} onChange={(e) => updatePayment(b._id, e.target.value)}>
                      {['pending', 'partial', 'paid', 'failed', 'refunded'].map((p) => <option key={p} value={p}>{p}</option>)}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {bookings && bookings.length === 0 && <p className="form-hint">No hay reservas para este filtro.</p>}
      </div>
    </div>
  );
}

// ============================================
// USUARIOS
// ============================================
function UsersAdmin() {
  const [users, setUsers] = useState(null);
  const toast = useToast();

  const load = () => {
    api.get('/auth/admin/users', { params: { limit: 50 } })
      .then((res) => setUsers(res.data.data))
      .catch(() => toast.error('No se pudieron cargar los usuarios'));
  };
  useEffect(load, []);

  const changeStatus = async (id, newStatus) => {
    try {
      await api.put(`/auth/admin/users/${id}/status`, { status: newStatus });
      toast.success('Estado actualizado');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo actualizar');
    }
  };

  const remove = async (id) => {
    if (!window.confirm('¿Eliminar este usuario?')) return;
    try {
      await api.delete(`/auth/admin/users/${id}`);
      toast.success('Usuario eliminado');
      load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'No se pudo eliminar');
    }
  };

  return (
    <div className="admin-tab">
      <div className="admin-table-wrap">
        {users === null && <div className="spinner" />}
        {users && (
          <table className="admin-table">
            <thead><tr><th>Nombre</th><th>Email</th><th>Rol</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              {users.map((u) => (
                <tr key={u._id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.role}</td>
                  <td>
                    <select value={u.status} onChange={(e) => changeStatus(u._id, e.target.value)}>
                      {['active', 'inactive', 'suspended'].map((s) => <option key={s} value={s}>{s}</option>)}
                    </select>
                  </td>
                  <td className="admin-row-actions">
                    <button className="link-btn danger" onClick={() => remove(u._id)}>Eliminar</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default function AdminPage() {
  const [tab, setTab] = useState('dashboard');

  return (
    <div className="dashboard-page">
      <Navbar />
      <div className="dashboard-shell">
        <div className="dashboard-head">
          <div>
            <p className="sec-label">Administración</p>
            <h1 className="sec-title">Panel de Palacio del Mar</h1>
          </div>
        </div>
        <div className="admin-tabs">
          {TABS.map((t) => (
            <button key={t.id} className={`filter-chip ${tab === t.id ? 'active' : ''}`} onClick={() => setTab(t.id)}>
              {t.label}
            </button>
          ))}
        </div>
        {tab === 'dashboard' && <DashboardAdmin />}
        {tab === 'suites' && <SuitesAdmin />}
        {tab === 'experiences' && <ExperiencesAdmin />}
        {tab === 'bookings' && <BookingsAdmin />}
        {tab === 'users' && <UsersAdmin />}
      </div>
      <Footer />
    </div>
  );
}
