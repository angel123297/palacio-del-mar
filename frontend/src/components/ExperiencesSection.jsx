import { useEffect, useState, useRef } from 'react';
import api from '../api/client';
import { useBookingCart } from '../context/BookingCartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCOP } from '../utils/format';
import { isObjectId } from '../utils/ids.js';

const FALLBACK_EXPERIENCES = [
  { _id: 'fx-1', name: 'Islas del Rosario', shortDescription: 'Excursión con snorkel y almuerzo', price: 348000, durationHours: 8, icon: '⛵', mainImage: 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-2', name: 'Atardecer en la Muralla', shortDescription: 'Cóctel y ron artesanal con vista al Caribe', price: 184000, durationHours: 2.5, icon: '🏛️', mainImage: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-3', name: 'Ruta del Sabor', shortDescription: 'Tour gastronómico con chef ejecutivo', price: 266000, durationHours: 4, icon: '🌿', mainImage: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=800&auto=format&fit=crop&q=80' },
  { _id: 'fx-4', name: 'Noche de Palenque', shortDescription: 'Música, baile y cena en terraza', price: 389000, durationHours: 3, icon: '🎶', mainImage: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80' }
];

export default function ExperiencesSection() {
  const [list, setList] = useState(null);
  const [categories, setCategories] = useState([]);
  const [catFilter, setCatFilter] = useState('');
  const [featuredIds, setFeaturedIds] = useState(new Set());
  const { experiences, toggleExperience } = useBookingCart();
  const toast = useToast();
  const sliderRef = useRef(null);

  useEffect(() => {
    api.get('/experiences', { params: { limit: 12 } })
      .then((res) => setList(res.data.data?.length ? res.data.data : FALLBACK_EXPERIENCES))
      .catch(() => setList(FALLBACK_EXPERIENCES));
  }, []);

  useEffect(() => {
    api.get('/experiences/categories')
      .then((res) => setCategories(res.data.data || []))
      .catch(() => setCategories([]));
    api.get('/experiences/featured', { params: { limit: 12 } })
      .then((res) => setFeaturedIds(new Set((res.data.data || []).map((e) => e._id))))
      .catch(() => setFeaturedIds(new Set()));
  }, []);

  const handleToggle = (exp) => {
    if (!isObjectId(exp._id) && !isObjectId(exp.id)) {
      toast.info('Esta experiencia no está disponible para reservar en este momento.');
      return;
    }
    const expId = exp._id || exp.id;
    const wasAdded = experiences.some((e) => (e._id || e.id) === expId);
    toggleExperience(exp);
    toast[wasAdded ? 'info' : 'success'](
      wasAdded ? `${exp.name} quitada de tu selección` : `${exp.name} agregada. Se suma cuando reserves una suite.`
    );
  };

  const scrollSlider = (direction) => {
    if (sliderRef.current) {
      const scrollAmount = direction === 'left' ? -320 : 320;
      sliderRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  if (!list) return null;

  const ordered = [...list].sort((a, b) => Number(featuredIds.has(b._id || b.id)) - Number(featuredIds.has(a._id || a.id)));
  const visible = catFilter ? ordered.filter((e) => e.category === catFilter) : ordered;
  const label = (c) => {
    if (!c) return '';
    const name = typeof c === 'string' ? c : (c.category || '');
    return name ? name.charAt(0).toUpperCase() + name.slice(1) : '';
  };

  return (
    <section id="experiences">
      <div className="rooms-header">
        <p className="sec-label">Vive Cartagena</p>
        <h2 className="sec-title">Experiencias añadidas</h2>
      </div>
      {categories.length > 0 && (
        <div className="filter-row" role="group" aria-label="Filtrar por categoría">
          <button type="button" className={`filter-btn ${catFilter === '' ? 'is-active' : ''}`} onClick={() => setCatFilter('')}>
            Todas
          </button>
          {categories.map((c) => {
            const catKey = typeof c === 'string' ? c : (c.category || '');
            const count = typeof c === 'object' && c?.count ? ` (${c.count})` : '';
            if (!catKey) return null;
            return (
              <button
                type="button"
                key={catKey}
                className={`filter-btn ${catFilter === catKey ? 'is-active' : ''}`}
                onClick={() => setCatFilter(catKey)}
              >
                {label(catKey)}{count}
              </button>
            );
          })}
        </div>
      )}
      
      <div className="exp-container">
        <button type="button" className="exp-nav-btn exp-nav-prev" onClick={() => scrollSlider('left')} aria-label="Anterior">
          ‹
        </button>
        <div className="exp-slider-wrapper">
          <div className="exp-grid" ref={sliderRef}>
            {visible.map((exp) => {
              const expId = exp._id || exp.id;
              const added = experiences.some((e) => (e._id || e.id) === expId);
              return (
                <div className="exp-card" key={expId}>
                  <div className="exp-img-wrap">
                    <img src={exp.mainImage || exp.image} alt={exp.name} loading="lazy" />
                    {featuredIds.has(expId) && <span className="exp-badge">Destacada</span>}
                  </div>
                  <div className="exp-content">
                    <span className="exp-icon">{exp.icon || '✨'}</span>
                    <h3>{exp.name}</h3>
                    <p>{exp.shortDescription || exp.description}</p>
                    <div className="exp-price-row">
                      <span className="exp-price">{formatCOP(exp.price)}</span>
                      {exp.durationHours && <span className="chip">{exp.durationHours}h</span>}
                    </div>
                    <button className={`exp-btn ${added ? 'exp-btn-added' : ''}`} onClick={() => handleToggle(exp)}>
                      {added ? '✓ Agregada' : 'Agregar'}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
        <button type="button" className="exp-nav-btn exp-nav-next" onClick={() => scrollSlider('right')} aria-label="Siguiente">
          ›
        </button>
      </div>

      {experiences.length > 0 && (
        <p className="sec-sub" style={{ textAlign: 'center', marginTop: '24px' }}>
          Tienes {experiences.length} experiencia(s) seleccionadas — se agregan automáticamente cuando reserves una suite.
        </p>
      )}
    </section>
  );
}
