import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <footer role="contentinfo" aria-label="Pie de página Palacio del Mar">
      <div class="footer-top">
        <div class="footer-col">
          <p class="footer-logo">Palacio del Mar</p>
          <p class="footer-desc">
            Colección exclusiva de suites y casonas de autor en Cartagena de Indias.
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
            <li><a href="mailto:reservas@palaciomar.co" class="footer-link" aria-label="Escribir correo a reservas@palaciomar.co">reservas&#64;palaciomar.co</a></li>
            <li><a href="https://wa.me/573009876543" target="_blank" rel="noopener noreferrer" class="footer-link" aria-label="Escribir por WhatsApp al +57 300 987 6543 (abre en nueva pestaña)">+57 300 987 6543</a></li>
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
    .footer-link { color: inherit; text-decoration: none; cursor: pointer; display: inline-block; padding: 4px 0; min-height: 28px; }
    .footer-link:hover { color: var(--gold); }
  `]
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
}
