import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <footer class="footer">
      <p>&copy; 2026 Palacio del Mar. Todos los derechos reservados.</p>
    </footer>
  `,
  styles: [`
    .footer { text-align: center; padding: 2rem; background: #0d0d0d; color: #d4af37; border-top: 1px solid #d4af37; }
  `]
})
export class FooterComponent {}
