import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/client';

export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const navigate = useNavigate();

  const [form, setForm] = useState({ newPassword: '', confirm: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    if (form.newPassword !== form.confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }

    setBusy(true);
    try {
      await api.post('/auth/reset-password', { token, newPassword: form.newPassword });
      setDone(true);
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo restablecer la contraseña');
    } finally {
      setBusy(false);
    }
  };

  if (!token) {
    return (
      <div className="simple-page">
        <div className="simple-card">
          <h1 className="sec-title">Enlace incompleto</h1>
          <p>Este enlace no incluye un token válido. Solicita uno nuevo desde "¿Olvidaste tu contraseña?".</p>
          <Link className="btn-primary" to="/">Ir al inicio</Link>
        </div>
      </div>
    );
  }

  if (done) {
    return (
      <div className="simple-page">
        <div className="simple-card">
          <h1 className="sec-title">Contraseña actualizada ✅</h1>
          <p>Ya puedes iniciar sesión con tu nueva contraseña.</p>
          <button className="btn-primary" onClick={() => navigate('/')}>Ir al inicio</button>
        </div>
      </div>
    );
  }

  return (
    <div className="simple-page">
      <form className="simple-card auth-form" onSubmit={submit}>
        <h1 className="sec-title">Nueva contraseña</h1>
        {error && <div className="form-error">{error}</div>}
        <label className="field-label">Nueva contraseña</label>
        <input
          type="password"
          required
          value={form.newPassword}
          onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
          placeholder="Mínimo 8 caracteres, con mayúscula y número"
        />
        <label className="field-label">Confirmar contraseña</label>
        <input
          type="password"
          required
          value={form.confirm}
          onChange={(e) => setForm({ ...form, confirm: e.target.value })}
        />
        <button className="btn-primary btn-block" type="submit" disabled={busy}>
          {busy ? 'Guardando…' : 'Restablecer contraseña'}
        </button>
      </form>
    </div>
  );
}
