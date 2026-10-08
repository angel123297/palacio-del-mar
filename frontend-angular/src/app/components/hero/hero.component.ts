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
        <p class="hero-eyebrow">Cartagena de Indias · 4 sucursales</p>
        <h1 class="hero-title">Palacio del Mar</h1>
        <p class="hero-sub">
          Suites de diseño exclusivo entre murallas coloniales, Getsemaní y el mar Caribe.
        </p>
        <div class="hero-ratings">
          <div class="hr-item">
            <span class="hr-score">9.6</span>
            <span class="hr-src">Booking.com</span>
          </div>
          <span class="hr-sep">·</span>
          <div class="hr-item">
            <span class="hr-score">4.9</span>
            <span class="hr-src">Tripadvisor</span>
          </div>
          <span class="hr-sep">·</span>
          <div class="hr-item">
            <span class="hr-score">97%</span>
            <span class="hr-src">Lo recomendarían</span>
          </div>
        </div>
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
