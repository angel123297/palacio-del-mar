import { useEffect, useState } from 'react';
import api from '../api/client';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP } from '../utils/format';

const FALLBACK_EXPERIENCES = [
  { _id: 'fx-1', name: 'Islas del Rosario', shortDescription: 'Excursión con snorkel y almuerzo', price: 348000, durationHours: 8, icon: '⛵', mainImage: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-2', name: 'Atardecer en la Muralla', shortDescription: 'Cóctel y ron artesanal con vista al Caribe', price: 184000, durationHours: 2.5, icon: '🏛️', mainImage: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-3', name: 'Ruta del Sabor', shortDescription: 'Tour gastronómico con chef ejecutivo', price: 266000, durationHours: 4, icon: '🌿', mainImage: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-4', name: 'Noche de Palenque', shortDescription: 'Música, baile y cena en terraza', price: 389000, durationHours: 3, icon: '🎶', mainImage: 'https://images.unsplash.com/photo-1534367507873-de2f85e8dbdf?w=800&auto=format&fit=crop&q=80' }
];

export default function ExperiencesSection() {
  const [list, setList] = useState(null);
  const { experiences, toggleExperience } = useBookingCart();
  const toast = useToast();

  useEffect(() => {
    api.get('/experiences', { params: { limit: 12 } })
      .then((res) => setList(res.data.data?.length ? res.data.data : FALLBACK_EXPERIENCES))
      .catch(() => setList(FALLBACK_EXPERIENCES));
  }, []);

  const handleToggle = (exp) => {
    const wasAdded = experiences.some((e) => e._id === exp._id);
    toggleExperience(exp);
    toast[wasAdded ? 'info' : 'success'](
      wasAdded ? `${exp.name} quitada de tu selección` : `${exp.name} agregada. Se suma cuando reserves una suite.`
    );
  };

  if (!list) return null;

  return (
    <section id="experiences">
      <div className="rooms-header">
        <p className="sec-label">Vive Cartagena</p>
        <h2 className="sec-title">Experiencias añadidas</h2>
      </div>
      <div className="exp-grid">
        {list.map((exp) => {
          const added = experiences.some((e) => e._id === exp._id);
          return (
            <div className="exp-card" key={exp._id}>
              <div className="room-img-wrap">
                <img src={exp.mainImage} alt={exp.name} loading="lazy" />
              </div>
              <span className="exp-icon">{exp.icon}</span>
              <h3>{exp.name}</h3>
              <p>{exp.shortDescription}</p>
              <div className="exp-price-row">
                <span className="exp-price">{formatCOP(exp.price)}</span>
                <span className="chip">{exp.durationHours}h</span>
              </div>
              <button className={`exp-btn ${added ? 'exp-btn-added' : ''}`} onClick={() => handleToggle(exp)}>
                {added ? '✓ Agregada' : 'Agregar'}
              </button>
            </div>
          );
        })}
      </div>
      {experiences.length > 0 && (
        <p className="sec-sub" style={{ textAlign: 'center' }}>
          Tienes {experiences.length} experiencia(s) seleccionadas — se agregan automáticamente cuando reserves una suite.
        </p>
      )}
    </section>
  );
}
