const TESTIMONIALS = [
  {
    id: 1,
    name: 'Carolina & Mateo R.',
    location: 'Bogotá, Colombia',
    rating: 5,
    text: 'Nuestra estancia en la Suite Presidencial del Centro Histórico fue inolvidable. La atención al detalle, el desayuno en el patio colonial y la vista a las murallas superaron todas nuestras expectativas.',
    suite: 'Suite Presidencial · Centro Histórico',
    date: 'Septiembre 2026'
  },
  {
    id: 2,
    name: 'Jean-Luc Dubois',
    location: 'París, Francia',
    rating: 5,
    text: 'Un auténtico palacio del siglo XVII equipado con el mejor lujo moderno. La excursión en yate a las Islas del Rosario gestionada desde la recepción fue el momento cumbre de nuestro viaje.',
    suite: 'Suite Deluxe · Getsemaní',
    date: 'Agosto 2026'
  },
  {
    id: 3,
    name: 'Sofia & David Miller',
    location: 'Miami, EE. UU.',
    rating: 5,
    text: 'El spa caribeño con masajes de barro volcánico y el restaurante de autor son sencillamente excepcionales. El equipo de Concierge estuvo atento a cada requerimiento.',
    suite: 'Suite Imperial · Centro Histórico',
    date: 'Julio 2026'
  }
];

export default function TestimonialsSection() {
  return (
    <section id="testimonials" style={{ padding: '4rem 1.5rem', backgroundColor: '#121212', borderTop: '1px solid rgba(212,175,55,0.15)' }}>
      <div className="rooms-header" style={{ textAlign: 'center', marginBottom: '3rem' }}>
        <p className="sec-label">Experiencias inolvidables</p>
        <h2 className="sec-title">Lo que dicen nuestros huéspedes</h2>
        <p className="sec-sub">Calificación promedio 9.8/10 basada en más de 450 reseñas verificadas</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
        {TESTIMONIALS.map((item) => (
          <div
            key={item.id}
            style={{
              background: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid rgba(212, 175, 55, 0.2)',
              borderRadius: '12px',
              padding: '2rem',
              display: 'flex',
              flexDirection: 'column',
              justify: 'space-between'
            }}
          >
            <div>
              <div style={{ color: 'var(--gold)', fontSize: '1.2rem', marginBottom: '0.75rem' }}>
                {'★'.repeat(item.rating)}
              </div>
              <p style={{ fontStyle: 'italic', color: '#e0e0e0', lineHeight: 1.6, marginBottom: '1.5rem' }}>
                "{item.text}"
              </p>
            </div>
            <div style={{ borderTop: '1px solid rgba(255, 255, 255, 0.08)', paddingTop: '1rem' }}>
              <strong style={{ display: 'block', color: '#ffffff' }}>{item.name}</strong>
              <span style={{ fontSize: '0.85rem', color: '#a0a0a0', display: 'block' }}>{item.location}</span>
              <span style={{ fontSize: '0.8rem', color: 'var(--gold)', display: 'block', marginTop: '4px' }}>{item.suite}</span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
