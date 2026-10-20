import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

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
            Marketplace de hospedaje con comisión baja en Cartagena de Indias.
          </p>
        </div>

        <div class="footer-col">
          <h4>Explorar</h4>
          <ul>
            <li><a routerLink="/" fragment="habitaciones" class="footer-link">Suites</a></li>
            <li><a routerLink="/" fragment="contact" class="footer-link">Eventos y contacto</a></li>
            <li><a routerLink="/" fragment="faq" class="footer-link">Preguntas frecuentes</a></li>
            <li><a routerLink="/calendario" class="footer-link">Calendario de disponibilidad</a></li>
          </ul>
        </div>

        <div class="footer-col">
          <h4>Contacto</h4>
          <ul>
            <li><a href="mailto:reservas@palaciomar.co" class="footer-link">reservas&#64;palaciomar.co</a></li>
            <li><a href="https://wa.me/573009876543" target="_blank" rel="noreferrer" class="footer-link">+57 300 987 6543</a></li>
            <li>Calle del Santísimo # 8-12, Centro Histórico</li>
          </ul>
        </div>
      </div>

      <div class="footer-bottom">
        <p>© {{ currentYear }} Palacio del Mar · Todos los derechos reservados.</p>
      </div>
    </footer>
  `,
  styles: [`
    .footer-link { color: inherit; text-decoration: none; cursor: pointer; }
    .footer-link:hover { color: var(--gold); }
  `]
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
}
