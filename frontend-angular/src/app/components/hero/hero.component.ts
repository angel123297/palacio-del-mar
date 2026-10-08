import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-hero',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="hero">
      <div
        className="hero-bg"
        style="background-image: url('https://images.unsplash.com/photo-1540541338287-41700207dee6?w=1600&auto=format&fit=crop&q=80')"
      ></div>
      <div className="hero-content">
        <p className="hero-eyebrow">Cartagena de Indias · 4 sucursales</p>
        <h1 className="hero-title">Palacio del Mar</h1>
        <p className="hero-sub">
          Suites de diseño exclusivo entre murallas coloniales, Getsemaní y el mar Caribe.
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
          <button className="btn-primary" (click)="scrollTo('#booking-bar')">Reservar ahora</button>
          <button className="btn-outline" (click)="scrollTo('#rooms')">Ver suites</button>
        </div>
      </div>
      <div className="scroll-hint">
        <span className="scroll-line"></span>
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
