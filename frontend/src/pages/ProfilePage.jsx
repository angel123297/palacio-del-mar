import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/client';
import Navbar from '../components/Navbar.jsx';
import Footer from '../components/Footer.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP, formatDate } from '../utils/format';
import { Geo } from '../components/admin/HollowIcons.jsx';
import '../styles/hk-user.css';

const NAME_RE = /^[a-zA-ZáéíóúñÁÉÍÓÚÑ\s]+$/;
const PHONE_RE = /^[+]?[(]?[0-9]{1,4}[)]?[-\s.]?[0-9]{1,4}[-\s.]?[0-9]{1,9}$/;
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/;

function ProfileStats() {
  const [stats, setStats] = useState(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api.get('/auth/profile/stats')
      .then((res) => setStats(res.data.data))
      .catch(() => setFailed(true));
  }, []);

  if (failed) return null; // las estadísticas son un extra: si fallan, no estorban
  if (!stats) return <div className="spinner" />;

  return (
    <div className="profile-stats">
      <div className="profile-stat">
        <strong>{formatDate(stats.memberSince)}</strong>
        <span>Miembro desde</span>
      </div>
      <div className="profile-stat">
        <strong>{stats.totalBookings}</strong>
        <span>Reservas</span>
      </div>
      <div className="profile-stat">
        <strong>{stats.completedBookings}</strong>
        <span>Estancias completadas</span>
      </div>
      <div className="profile-stat">
        <strong><Geo />{formatCOP(stats.totalSpent)}</strong>
        <span>Total en estancias completadas</span>
      </div>
    </div>
  );
}

function PersonalDataForm() {
  const { user, refreshUser } = useAuth();
  const toast = useToast();
  const [name, setName] = useState(user?.name || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const unchanged = name.trim() === (user?.name || '') && phone.trim() === (user?.phone || '');

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    if (name.trim().length < 2) return setError('El nombre debe tener al menos 2 caracteres');
    if (!NAME_RE.test(name.trim())) return setError('El nombre solo puede contener letras y espacios');
    if (!PHONE_RE.test(phone.trim())) return setError('El teléfono no es válido');

    setBusy(true);
    try {
      await api.put('/auth/profile', { name: name.trim(), phone: phone.trim() });
      await refreshUser();
      toast.success('Perfil actualizado');
    } catch (err) {
      setError(err.response?.data?.errors?.[0] || err.response?.data?.message || 'No se pudo actualizar el perfil');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="admin-form profile-form" onSubmit={submit}>
      <h2 className="profile-form-title">Datos personales</h2>
      {error && <div className="form-error">{error}</div>}
      <label className="field-label">Nombre</label>
      <input value={name} onChange={(e) => setName(e.target.value)} required />
      <label className="field-label">Correo electrónico</label>
      <input value={user?.email || ''} disabled />
      <p className="form-hint">El correo no se puede cambiar desde aquí.</p>
      <label className="field-label">Teléfono</label>
      <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="300 123 4567" required />
      <button className="btn-primary" type="submit" disabled={busy || unchanged}>
        {busy ? 'Guardando…' : 'Guardar cambios'}
      </button>
    </form>
  );
}

function ChangePasswordForm() {
  const { refreshUser } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    if (!PASSWORD_RE.test(form.newPassword)) {
      return setError('La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un número');
    }
    if (form.newPassword !== form.confirmPassword) {
      return setError('Las contraseñas nuevas no coinciden');
    }

    setBusy(true);
    try {
      const res = await api.post('/auth/change-password', {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword
      });
      // El backend invalida los tokens anteriores al cambio y devuelve uno
      // nuevo: lo guardamos para no sacar al usuario de su sesión.
      if (res.data.token) localStorage.setItem('palacio_token', res.data.token);
      await refreshUser();
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      toast.success('Contraseña actualizada');
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo cambiar la contraseña');
    } finally {
      setBusy(false);
    }
  };

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <form className="admin-form profile-form" onSubmit={submit}>
      <h2 className="profile-form-title">Cambiar contraseña</h2>
      {error && <div className="form-error">{error}</div>}
      <label className="field-label">Contraseña actual</label>
      <input type="password" value={form.currentPassword} onChange={set('currentPassword')} autoComplete="current-password" required />
      <label className="field-label">Nueva contraseña</label>
      <input type="password" value={form.newPassword} onChange={set('newPassword')} autoComplete="new-password" required />
      <label className="field-label">Repite la nueva contraseña</label>
      <input type="password" value={form.confirmPassword} onChange={set('confirmPassword')} autoComplete="new-password" required />
      <button className="btn-primary" type="submit" disabled={busy}>
        {busy ? 'Guardando…' : 'Cambiar contraseña'}
      </button>
    </form>
  );
}

export default function ProfilePage() {
  return (
    <div className="dashboard-page hk-user">
      <Navbar />
      <div className="dashboard-shell">
        <div className="dashboard-head">
          <div>
            <p className="sec-label">Mi cuenta</p>
            <h1 className="sec-title">Mi perfil</h1>
          </div>
          <Link className="btn-outline" to="/dashboard">← Mis reservas</Link>
        </div>

        <ProfileStats />

        <div className="profile-grid">
          <PersonalDataForm />
          <ChangePasswordForm />
        </div>
      </div>
      <Footer />
    </div>
  );
}
