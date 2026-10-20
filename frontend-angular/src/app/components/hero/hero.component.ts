import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-hero',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="hero">
      <div
        class="hero-bg"
        style="background-image: url('https://images.unsplash.com/photo-1540541338287-41700207dee6?w=1200&auto=format&fit=crop&q=70')"
      ></div>
      <div class="hero-content">
        <p class="hero-eyebrow">Cartagena de Indias · Marketplace de hospedaje</p>
        <h1 class="hero-title">Palacio del Mar</h1>
        <p class="hero-sub">
          Suites de diseño exclusivo entre murallas coloniales, Getsemaní y el mar Caribe.
        </p>
        <div class="hero-actions">
          <button class="btn-primary" (click)="scrollTo('#booking-bar')">Reservar ahora</button>
          <button class="btn-outline" (click)="scrollTo('#rooms')">Ver suites</button>
        </div>
      </div>
      <div class="scroll-hint">
        <span class="scroll-line"></span>
        <span>Desliza</span>
      </div>
    </section>
  `
})
export class HeroComponent {
  scrollTo(selector: string): void {
    document.querySelector(selector)?.scrollIntoView({ behavior: 'smooth' });
  }
}
