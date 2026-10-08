import { useState } from 'react';

const FAQS = [
  {
    q: '¿Cuáles son los horarios de Check-in y Check-out?',
    a: 'El Check-in está disponible a partir de las 15:00 hrs y el Check-out se realiza hasta las 12:00 hrs del mediodía. Si necesitas check-in temprano o salida tardía, puedes solicitarlo previamente con nuestro equipo de Concierge.'
  },
  {
    q: '¿Cuál es la política de cancelación y reembolso?',
    a: 'Las reservas pueden cancelarse sin cargo directo desde tu panel de usuario. En caso de contar con un abono previo, el reembolso del importe pagado se realiza automáticamente a tu cuenta bancaria o método de pago en un plazo garantizado de 24 a 48 horas.'
  },
  {
    q: '¿Aceptan mascotas en las sucursales?',
    a: 'Aceptamos mascotas pequeñas (hasta 10 kg) en sucursales seleccionadas previa confirmación y disponibilidad de habitaciones dog-friendly equipadas con cama y tazón de bienvenida.'
  },
  {
    q: '¿Cuentan con servicio de transporte desde el aeropuerto?',
    a: 'Sí, ofrecemos servicio VIP de traslado en vehículo ejecutivo privado o camioneta de lujo desde el Aeropuerto Internacional Rafael Núñez directamente a la sucursal de tu elección.'
  },
  {
    q: '¿Qué formas de pago aceptan?',
    a: 'Aceptamos tarjetas de crédito y débito (Visa, Mastercard, American Express), transferencias bancarias, PSE y pagos con saldo a través de nuestra pasarela segura integrada.'
  }
];

export default function FaqSection() {
  const [openIdx, setOpenIdx] = useState(0);

  const toggle = (idx) => {
    setOpenIdx(openIdx === idx ? null : idx);
  };

  return (
    <section id="faq" style={{ padding: '4rem 1.5rem', backgroundColor: '#0d0d0d' }}>
      <div style={{ maxWidth: '900px', margin: '0 auto' }}>
        <div className="rooms-header" style={{ textAlign: 'center', marginBottom: '3rem' }}>
          <p className="sec-label">Preguntas frecuentes</p>
          <h2 className="sec-title">Resuelve tus dudas antes de viajar</h2>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {FAQS.map((faq, idx) => {
            const isOpen = openIdx === idx;
            return (
              <div
                key={idx}
                style={{
                  background: 'rgba(255, 255, 255, 0.02)',
                  border: '1px solid rgba(212, 175, 55, 0.2)',
                  borderRadius: '8px',
                  overflow: 'hidden'
                }}
              >
                <button
                  type="button"
                  onClick={() => toggle(idx)}
                  style={{
                    width: '100%',
                    padding: '1.25rem 1.5rem',
                    display: 'flex',
                    justify: 'space-between',
                    alignItems: 'center',
                    background: 'none',
                    border: 'none',
                    color: '#ffffff',
                    fontSize: '1.05rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    textAlign: 'left'
                  }}
                >
                  <span>{faq.q}</span>
                  <span style={{ color: 'var(--gold)', fontSize: '1.3rem', marginLeft: '1rem' }}>
                    {isOpen ? '−' : '+'}
                  </span>
                </button>
                {isOpen && (
                  <div
                    style={{
                      padding: '0 1.5rem 1.25rem 1.5rem',
                      color: '#cccccc',
                      lineHeight: 1.6,
                      borderTop: '1px solid rgba(255, 255, 255, 0.05)'
                    }}
                  >
                    <p style={{ margin: 0 }}>{faq.a}</p>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
