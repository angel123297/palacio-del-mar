import { useState } from 'react';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import api from '../api/client';

function ModalShell({ onClose, children, wide = false }) {
  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal-box ${wide ? 'modal-wide' : ''}`}>
        <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        {children}
      </div>
    </div>
  );
}

function LoginForm({ onSwitch, onClose }) {
  const { login } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ email: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const user = await login(form.email.trim(), form.password);
      toast.success(`Bienvenido de nuevo, ${user.name.split(' ')[0]}`);
    } catch (err) {
      setError(err.response?.data?.message || 'No se pudo iniciar sesión. Intenta de nuevo.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="auth-form" onSubmit={submit}>
      <p className="modal-eyebrow">Bienvenido de nuevo</p>
      <h2 className="modal-title">Iniciar sesión</h2>
      {error && <div className="form-error">{error}</div>}
      <label className="field-label">Correo electrónico</label>
      <input
        type="email"
        required
        value={form.email}
        onChange={(e) => setForm({ ...form, email: e.target.value })}
        placeholder="tu@email.com"
      />
      <label className="field-label">Contraseña</label>
      <input
        type="password"
        required
        value={form.password}
        onChange={(e) => setForm({ ...form, password: e.target.value })}
        placeholder="••••••••"
      />
      <button type="button" className="link-btn auth-forgot" onClick={() => onSwitch('forgot')}>
        ¿Olvidaste tu contraseña?
      </button>
      <button className="btn-primary btn-block" type="submit" disabled={busy}>
        {busy ? 'Entrando…' : 'Iniciar sesión'}
      </button>
      <p className="auth-switch">
        ¿No tienes cuenta?{' '}
        <button type="button" className="link-btn" onClick={() => onSwitch('register')}>
          Regístrate
        </button>
      </p>
    </form>
  );
}

function RegisterForm({ onSwitch }) {
  const { register } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ name: '', email: '', password: '', confirmPassword: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');

    if (form.password !== form.confirmPassword) {
      setError('Las contraseñas no coinciden');
      return;
    }
    if (form.password.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres');
      return;
    }

    setBusy(true);
    try {
      // confirmPassword es solo una validación en el navegador: el backend
      // no acepta ese campo (Joi rechaza claves desconocidas), así que
      // solo se envían name/email/password.
      const user = await register(form.name.trim(), form.email.trim(), form.password);
      toast.success(`Cuenta creada. ¡Bienvenido, ${user.name.split(' ')[0]}!`);
    } catch (err) {
      const msg = err.response?.data?.message;
      const detail = err.response?.data?.errors?.[0];
      setError(detail || msg || 'No se pudo crear la cuenta. Intenta de nuevo.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="auth-form" onSubmit={submit}>
      <p className="modal-eyebrow">Únete a Palacio del Mar</p>
      <h2 className="modal-title">Crear cuenta</h2>
      {error && <div className="form-error">{error}</div>}
      <label className="field-label">Nombre completo</label>
      <input
        required
        value={form.name}
        onChange={(e) => setForm({ ...form, name: e.target.value })}
        placeholder="Tu nombre"
      />
      <label className="field-label">Correo electrónico</label>
      <input
        type="email"
        required
        value={form.email}
        onChange={(e) => setForm({ ...form, email: e.target.value })}
        placeholder="tu@email.com"
      />
      <label className="field-label">Contraseña</label>
      <input
        type="password"
        required
        value={form.password}
        onChange={(e) => setForm({ ...form, password: e.target.value })}
        placeholder="Mínimo 8 caracteres, con mayúscula y número"
      />
      <label className="field-label">Confirmar contraseña</label>
      <input
        type="password"
        required
        value={form.confirmPassword}
        onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
        placeholder="••••••••"
      />
      <button className="btn-primary btn-block" type="submit" disabled={busy}>
        {busy ? 'Creando cuenta…' : 'Crear cuenta'}
      </button>
      <p className="auth-switch">
        ¿Ya tienes cuenta?{' '}
        <button type="button" className="link-btn" onClick={() => onSwitch('login')}>
          Inicia sesión
        </button>
      </p>
    </form>
  );
}

function ForgotForm({ onSwitch }) {
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await api.post('/auth/forgot-password', { email: email.trim() });
      setSent(true);
    } catch {
      // Por seguridad el backend siempre responde 200 aunque el email no
      // exista, así que un error aquí es un fallo real de red/servidor.
      toast.error('No se pudo procesar la solicitud. Intenta más tarde.');
    } finally {
      setBusy(false);
    }
  };

  if (sent) {
    return (
      <div className="auth-form">
        <p className="modal-eyebrow">Revisa tu correo</p>
        <h2 className="modal-title">Enlace enviado</h2>
        <p className="modal-copy">
          Si <strong>{email}</strong> está registrado, te llegará un correo con un enlace para
          restablecer tu contraseña.
        </p>
        <button type="button" className="btn-outline btn-block" onClick={() => onSwitch('login')}>
          Volver a iniciar sesión
        </button>
      </div>
    );
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <p className="modal-eyebrow">Recuperar acceso</p>
      <h2 className="modal-title">¿Olvidaste tu contraseña?</h2>
      <p className="modal-copy">Escribe tu correo y te enviaremos un enlace para restablecerla.</p>
      <label className="field-label">Correo electrónico</label>
      <input
        type="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="tu@email.com"
      />
      <button className="btn-primary btn-block" type="submit" disabled={busy}>
        {busy ? 'Enviando…' : 'Enviar enlace'}
      </button>
      <p className="auth-switch">
        <button type="button" className="link-btn" onClick={() => onSwitch('login')}>
          ← Volver a iniciar sesión
        </button>
      </p>
    </form>
  );
}

export default function AuthModals() {
  const { authModal, setAuthModal } = useAuth();

  if (!authModal) return null;

  const switchTo = (m) => setAuthModal(m);
  const close = () => setAuthModal(null);

  return (
    <ModalShell onClose={close}>
      {authModal === 'login' && <LoginForm onSwitch={switchTo} onClose={close} />}
      {authModal === 'register' && <RegisterForm onSwitch={switchTo} />}
      {authModal === 'forgot' && <ForgotForm onSwitch={switchTo} />}
    </ModalShell>
  );
}
