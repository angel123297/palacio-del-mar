import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <footer>
      <div class="footer-top">
        <div class="footer-col">
          <p class="footer-logo">Palacio del Mar</p>
          <p class="footer-desc">
            Hotel boutique de lujo en el centro histórico amurallado de Cartagena de Indias.
          </p>
          <div class="footer-awards">
            <span class="footer-award">🏆 Travellers' Choice 2026</span>
            <span class="footer-award">⭐ 9.8 Booking.com</span>
          </div>
        </div>

        <div class="footer-col">
          <h4>Explorar</h4>
          <ul>
            <li (click)="scrollTo('#rooms')">Suites</li>
            <li (click)="scrollTo('#experiences')">Experiencias</li>
            <li (click)="scrollTo('#dining')">Gastronomía</li>
            <li (click)="scrollTo('#spa')">Spa</li>
            <li (click)="scrollTo('#faq')">Preguntas frecuentes</li>
          </ul>
        </div>

        <div class="footer-col">
          <h4>Contacto VIP</h4>
          <ul>
            <li><a href="mailto:reservas@palaciomar.co" style="color: inherit; text-decoration: none;">reservas&#64;palaciomar.co</a></li>
            <li><a href="https://wa.me/573009876543" target="_blank" rel="noreferrer" style="color: inherit; text-decoration: none;">+57 300 987 6543</a></li>
            <li>Calle del Santísimo # 8-12, Centro Histórico</li>
          </ul>
        </div>

        <div class="footer-col">
          <h4>Síguenos</h4>
          <div class="social-links">
            <a href="https://instagram.com" target="_blank" rel="noreferrer">Instagram</a>
            <a href="https://facebook.com" target="_blank" rel="noreferrer">Facebook</a>
          </div>
        </div>
      </div>

      <div class="footer-bottom">
        <p>© {{ currentYear }} Palacio del Mar · Todos los derechos reservados.</p>
      </div>
    </footer>
  `
})
export class FooterComponent {
  currentYear = new Date().getFullYear();

  scrollTo(selector: string): void {
    document.querySelector(selector)?.scrollIntoView({ behavior: 'smooth' });
  }
}
