import { useState } from 'react';

const TAGS = ['Baños de barro', 'Ritmos caribeños', 'Aceites autóctonos', 'Terapia de piedras'];

const SPA_TREATMENTS = [
  { name: 'Ritual Caribeño Completo (90 min)', desc: 'Exfoliación con sal marina y coco, masaje corporal relajante y mascarilla facial de barro volcánico del Totumo.', price: '$ 320.000' },
  { name: 'Masaje de Piedras Volcánicas (60 min)', desc: 'Terapia térmica relajante con piedras de lava y aceites autóctonos de jazmín y ylang-ylang.', price: '$ 240.000' },
  { name: 'Hidroterapia & Jacuzzi Privado (45 min)', desc: 'Circuito de aguas aromatizadas con eucalipto y vista panorámica a la bahía amurallada.', price: '$ 180.000' },
  { name: 'Masaje en Pareja frente al Mar (75 min)', desc: 'Sesión de relajación para dos personas en pérgola privada con copa de champán.', price: '$ 490.000' }
];

export default function SpaSection() {
  const [showSpaModal, setShowSpaModal] = useState(false);

  return (
    <section id="spa">
      <div
        className="spa-bg"
        style={{
          backgroundImage:
            "url('https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=1400&auto=format&fit=crop&q=80')"
        }}
      />
      <div className="spa-content">
        <p className="spa-tag">Bienestar</p>
        <h2 className="sec-title">Spa Caribe</h2>
        <p>
          Tratamientos con ingredientes autóctonos del Caribe colombiano: barro volcánico,
          coco, tabaco y ron añejo. Circuito de aguas con vista a la bahía y masajes
          inspirados en los ritmos de Palenque.
        </p>
        <div className="spa-tags">
          {TAGS.map((t) => (
            <span className="perk-tag" key={t}>{t}</span>
          ))}
        </div>
        <button
          type="button"
          className="btn-outline"
          style={{ marginTop: '1.5rem', borderColor: '#ffffff', color: '#ffffff' }}
          onClick={() => setShowSpaModal(true)}
        >
          🌿 Ver Menú de Tratamientos Spa
        </button>
      </div>

      {showSpaModal && (
        <div className="modal-backdrop" onClick={() => setShowSpaModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '650px', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', borderBottom: '1px solid rgba(212,175,55,0.3)', paddingBottom: '1rem' }}>
              <h3 style={{ margin: 0, color: 'var(--gold)' }}>🌺 Carta de Servicios & Tratamientos Spa Caribe</h3>
              <button className="link-btn" onClick={() => setShowSpaModal(false)} style={{ fontSize: '1.5rem' }}>×</button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              {SPA_TREATMENTS.map((treatment, i) => (
                <div key={i} style={{ borderBottom: '1px solid rgba(255,255,255,0.08)', pb: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#ffffff', fontWeight: 600 }}>
                    <span>{treatment.name}</span>
                    <span style={{ color: 'var(--gold)' }}>{treatment.price}</span>
                  </div>
                  <p style={{ color: '#aaaaaa', fontSize: '0.9rem', margin: '4px 0 0 0' }}>{treatment.desc}</p>
                </div>
              ))}
            </div>
            <div style={{ textAlign: 'center', marginTop: '1.5rem' }}>
              <button className="btn-primary" onClick={() => setShowSpaModal(false)}>Cerrar Menú</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
