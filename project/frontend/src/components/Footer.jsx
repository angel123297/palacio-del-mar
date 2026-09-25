export default function Footer() {
  const year = new Date().getFullYear();
  return (
    <footer>
      <div className="footer-top">
        <div className="footer-col">
          <p className="footer-logo">Palacio del Mar</p>
          <p className="footer-desc">
            Hotel boutique de lujo en el centro histórico amurallado de Cartagena de Indias.
          </p>
          <div className="footer-awards">
            <span className="footer-award">🏆 Travellers' Choice 2025</span>
            <span className="footer-award">⭐ 9.6 Booking.com</span>
          </div>
        </div>
        <div className="footer-col">
          <strong>Explorar</strong>
          <a href="#rooms">Suites</a>
          <a href="#experiences">Experiencias</a>
          <a href="#dining">Gastronomía</a>
          <a href="#spa">Spa</a>
        </div>
        <div className="footer-col">
          <strong>Contacto</strong>
          <a href="mailto:reservas@palaciomar.co">reservas@palaciomar.co</a>
          <a href="https://wa.me/573000000000" target="_blank" rel="noreferrer">+57 300 000 0000</a>
          <span>Calle del Santísimo, Centro Histórico, Cartagena</span>
        </div>
        <div className="footer-col">
          <strong>Síguenos</strong>
          <div className="social-links">
            <a href="https://instagram.com" target="_blank" rel="noreferrer">Instagram</a>
            <a href="https://facebook.com" target="_blank" rel="noreferrer">Facebook</a>
          </div>
        </div>
      </div>
      <div className="footer-bottom">
        <span>© {year} Palacio del Mar. Todos los derechos reservados.</span>
      </div>
    </footer>
  );
}
