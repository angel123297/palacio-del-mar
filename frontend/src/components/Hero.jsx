export default function Hero() {
  const scrollToBooking = () => {
    document.querySelector('#booking-bar')?.scrollIntoView({ behavior: 'smooth' });
  };
  const scrollToRooms = () => {
    document.querySelector('#rooms')?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <section id="hero">
      <div
        className="hero-bg"
        style={{
          backgroundImage:
            "url('https://images.unsplash.com/photo-1540541338287-41700207dee6?w=1600&auto=format&fit=crop&q=80')"
        }}
      />
      <div className="hero-content">
        <p className="hero-eyebrow">Cartagena de Indias · Centro Histórico</p>
        <h1 className="hero-title">Palacio del Mar</h1>
        <p className="hero-sub">
          18 suites de diseño exclusivo entre murallas coloniales y el mar Caribe.
        </p>
        <div className="hero-ratings">
          <div className="hr-item">
            <span className="hr-score">9.6</span>
            <span className="hr-src">Booking.com</span>
          </div>
          <span className="hr-sep">·</span>
          <div className="hr-item">
            <span className="hr-score">4.9</span>
            <span className="hr-src">Tripadvisor</span>
          </div>
          <span className="hr-sep">·</span>
          <div className="hr-item">
            <span className="hr-score">97%</span>
            <span className="hr-src">Lo recomendarían</span>
          </div>
        </div>
        <div className="hero-actions">
          <button className="btn-primary" onClick={scrollToBooking}>Reservar ahora</button>
          <button className="btn-outline" onClick={scrollToRooms}>Ver suites</button>
        </div>
      </div>
      <div className="scroll-hint">
        <span className="scroll-line" />
        <span>Desliza</span>
      </div>
    </section>
  );
}
