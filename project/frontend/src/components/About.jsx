export default function About() {
  return (
    <section id="about">
      <div className="about-img-wrap">
        <img
          src="https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?w=900&auto=format&fit=crop&q=80"
          alt="Patio colonial de Palacio del Mar"
          loading="lazy"
        />
        <span className="about-badge">
          <span className="num">2016</span>
          <span className="lbl">Desde</span>
        </span>
      </div>
      <div className="about-text">
        <p className="sec-label">Nuestra historia</p>
        <h2 className="sec-title">Un palacio colonial, reinventado</h2>
        <p>
          Palacio del Mar ocupa una casona del siglo XVII en el corazón amurallado de
          Cartagena de Indias. Restauramos cada patio, cada balcón de madera y cada
          baldosín original para convertirlos en un refugio íntimo de 18 suites, donde
          la historia colonial convive con el confort contemporáneo.
        </p>
        <div className="about-features">
          <div className="feat">
            <strong>18</strong>
            <span>suites de diseño exclusivo</span>
          </div>
          <div className="feat">
            <strong>1</strong>
            <span>spa caribeño con tratamientos autóctonos</span>
          </div>
          <div className="feat">
            <strong>97%</strong>
            <span>de huéspedes lo recomendarían</span>
          </div>
        </div>
      </div>
    </section>
  );
}
