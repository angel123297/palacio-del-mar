import { Component } from '@angular/core';

@Component({
  selector: 'app-hero',
  standalone: true,
  template: `
    <div class="hero">
      <h1>Lujo y confort frente al mar</h1>
      <p>Descubre nuestras exclusivas suites en Cartagena</p>
    </div>
  `,
  styles: [`
    .hero { text-align: center; padding: 5rem 2rem; background: #1a1a1a; color: white; }
    .hero h1 { color: #d4af37; }
  `]
})
export class HeroComponent {}
