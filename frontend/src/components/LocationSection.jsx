const ITEMS = [
  { icon: '✈️', text: '15 min desde el Aeropuerto Rafael Núñez' },
  { icon: '🚶', text: '2 min caminando desde la Plaza de Bolívar' },
  { icon: '🏰', text: 'A pasos de la Catedral y los mejores restaurantes' }
];

export default function LocationSection() {
  return (
    <section id="location">
      <div className="location-content">
        <p className="sec-label">Ubicación</p>
        <h2 className="sec-title">En el corazón de Cartagena</h2>
        <p>
          A pasos de la Plaza de Bolívar, la Catedral y los mejores restaurantes del centro
          histórico. El mundo colonial a tu alcance.
        </p>
        <div className="location-items">
          {ITEMS.map((i) => (
            <div className="loc-item" key={i.text}>
              <span className="loc-icon">{i.icon}</span>
              <span>{i.text}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="location-map">
        <iframe
          title="Ubicación de Palacio del Mar"
          src="https://www.openstreetmap.org/export/embed.html?bbox=-75.5540%2C10.4210%2C-75.5470%2C10.4270&layer=mapnik&marker=10.4236%2C-75.5510"
          loading="lazy"
        />
      </div>
    </section>
  );
}
