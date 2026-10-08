import { useState } from 'react';

const DISHES = [
  { num: '01', name: 'Ceviche de camarón al coco', desc: 'Camarón fresco, leche de coco, ají dulce y plátano verde.', price: '$ 68.000' },
  { num: '02', name: 'Arroz de mariscos del Caribe', desc: 'Arroz meloso con langosta, camarón y calamar de la bahía.', price: '$ 125.000' },
  { num: '03', name: 'Posta cartagenera', desc: 'Carne braseada en salsa de panela, vino tinto y especias.', price: '$ 94.000' },
  { num: '04', name: 'Cocada horneada', desc: 'Coco caramelizado con helado de maracuyá.', price: '$ 38.000' }
];

const EXTRA_MENU = [
  { category: 'Entradas', items: ['Tartar de atún con mango biche - $ 72.000', 'Carpaccio de pulpo al maracuyá - $ 65.000', 'Empanaditas de jaiba y suero costeño - $ 48.000'] },
  { category: 'Fuertes', items: ['Pescado frito entero con arroz de coco - $ 110.000', 'Mero en salsa de cazuela caribeña - $ 135.000', 'Lomo al trapo con mantequilla de corozo - $ 120.000'] },
  { category: 'Cócteles de Autor', items: ['Cartagena Mule (Ron añejo, jengibre, maracuyá) - $ 45.000', 'Mojito de Corozo - $ 42.000', 'Gin Tonic Caribeño - $ 48.000'] }
];

export default function DiningSection() {
  const [showMenu, setShowMenu] = useState(false);

  return (
    <section id="dining">
      <div className="dining-content">
        <p className="sec-label">Gastronomía</p>
        <h2 className="sec-title">Gastronomía de autor</h2>
        <p>
          Nuestro chef ejecutivo reinterpreta la cocina caribeña con producto local: pesca del
          día, coco, plátano y especias del Caribe colombiano, servidos en el patio central bajo
          las estrellas.
        </p>
        <div className="dining-items">
          {DISHES.map((d) => (
            <div className="dining-item" key={d.num}>
              <span className="dining-num">{d.num}</span>
              <div>
                <strong>{d.name} <span style={{ color: 'var(--gold)', marginLeft: '8px', fontSize: '0.9rem' }}>{d.price}</span></strong>
                <p>{d.desc}</p>
              </div>
            </div>
          ))}
        </div>
        <button
          type="button"
          className="btn-outline"
          style={{ marginTop: '1.5rem' }}
          onClick={() => setShowMenu(true)}
        >
          📖 Ver Menú Completo & Carta de Vinos
        </button>
      </div>

      <div className="dining-img">
        <img
          src="https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=900&auto=format&fit=crop&q=80"
          alt="Restaurante de Palacio del Mar"
          loading="lazy"
        />
      </div>

      {showMenu && (
        <div className="modal-backdrop" onClick={() => setShowMenu(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()} style={{ maxWidth: '650px', padding: '2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', borderBottom: '1px solid rgba(212,175,55,0.3)', paddingBottom: '1rem' }}>
              <h3 style={{ margin: 0, color: 'var(--gold)' }}>🍽️ Carta del Restaurante Palacio del Mar</h3>
              <button className="link-btn" onClick={() => setShowMenu(false)} style={{ fontSize: '1.5rem' }}>×</button>
            </div>
            {EXTRA_MENU.map((section, idx) => (
              <div key={idx} style={{ marginBottom: '1.5rem' }}>
                <h4 style={{ color: '#ffffff', marginBottom: '0.5rem', borderBottom: '1px dashed rgba(255,255,255,0.1)', paddingBottom: '4px' }}>
                  {section.category}
                </h4>
                <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                  {section.items.map((item, i) => (
                    <li key={i} style={{ color: '#cccccc', padding: '6px 0', fontSize: '0.95rem' }}>{item}</li>
                  ))}
                </ul>
              </div>
            ))}
            <div style={{ textAlign: 'center', marginTop: '1.5rem' }}>
              <button className="btn-primary" onClick={() => setShowMenu(false)}>Cerrar Menú</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
