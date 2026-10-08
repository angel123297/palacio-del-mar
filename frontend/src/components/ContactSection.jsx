import { useState } from 'react';
import { useToast } from '../context/ToastContext.jsx';

export default function ContactSection() {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone: '',
    eventType: 'consulta',
    message: ''
  });
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  const handleSubmit = (e) => {
    e.preventDefault();
    setBusy(true);
    setTimeout(() => {
      setBusy(false);
      toast.success('¡Gracias por contactarnos! Un asesor de Palacio del Mar se comunicará contigo en breve.');
      setFormData({ name: '', email: '', phone: '', eventType: 'consulta', message: '' });
    }, 800);
  };

  return (
    <section id="contact" style={{ padding: '4rem 1.5rem', backgroundColor: '#121212', borderTop: '1px solid rgba(212,175,55,0.15)' }}>
      <div style={{ maxWidth: '1000px', margin: '0 auto', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '3rem' }}>
        <div>
          <p className="sec-label">Atención personalizada</p>
          <h2 className="sec-title">Eventos, Bodas y Consultas VIP</h2>
          <p style={{ color: '#cccccc', lineHeight: 1.6, marginBottom: '2rem' }}>
            ¿Planeas una boda colonial en Cartagena, un retiro corporativo o una celebración exclusiva en nuestras sucursales? Nuestro equipo de concierge planificará cada detalle.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', color: '#e0e0e0' }}>
            <div>
              <strong style={{ color: 'var(--gold)', display: 'block' }}>📍 Dirección Principal</strong>
              <span>Calle del Santísimo # 8-12, Centro Histórico, Cartagena de Indias</span>
            </div>
            <div>
              <strong style={{ color: 'var(--gold)', display: 'block' }}>📞 Teléfono & WhatsApp</strong>
              <span>+57 300 987 6543 / +57 (605) 650 1234</span>
            </div>
            <div>
              <strong style={{ color: 'var(--gold)', display: 'block' }}>✉️ Correo Electrónico</strong>
              <span>reservas@palaciomar.co · eventos@palaciomar.co</span>
            </div>
          </div>
        </div>

        <form
          onSubmit={handleSubmit}
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            border: '1px solid rgba(212, 175, 55, 0.2)',
            borderRadius: '12px',
            padding: '2rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '1rem'
          }}
        >
          <h3 style={{ color: '#ffffff', margin: '0 0 0.5rem 0' }}>Envíanos tu solicitud</h3>
          <div>
            <label className="field-label" htmlFor="c-name">Nombre completo</label>
            <input
              id="c-name"
              type="text"
              required
              placeholder="Ej: Alejandro Silva"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label className="field-label" htmlFor="c-email">Correo electrónico</label>
              <input
                id="c-email"
                type="email"
                required
                placeholder="ejemplo@correo.com"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
            </div>
            <div>
              <label className="field-label" htmlFor="c-phone">Teléfono</label>
              <input
                id="c-phone"
                type="tel"
                placeholder="+57 300..."
                value={formData.phone}
                onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              />
            </div>
          </div>

          <div>
            <label className="field-label" htmlFor="c-type">Tipo de solicitud</label>
            <select
              id="c-type"
              value={formData.eventType}
              onChange={(e) => setFormData({ ...formData, eventType: e.target.value })}
            >
              <option value="consulta">Consulta General</option>
              <option value="boda">Boda o Aniversario</option>
              <option value="corporativo">Evento Corporativo</option>
              <option value="yate">Alquiler Privado de Yate</option>
            </select>
          </div>

          <div>
            <label className="field-label" htmlFor="c-msg">Mensaje o requerimientos especiales</label>
            <textarea
              id="c-msg"
              rows={4}
              required
              placeholder="Cuéntanos sobre tus fechas estimadas, número de invitados o requerimientos..."
              value={formData.message}
              onChange={(e) => setFormData({ ...formData, message: e.target.value })}
            />
          </div>

          <button className="btn-primary" type="submit" disabled={busy}>
            {busy ? 'Enviando solicitud…' : 'Enviar Mensaje'}
          </button>
        </form>
      </div>
    </section>
  );
}
