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
        style="background-image: linear-gradient(to bottom, rgba(13, 13, 11, 0.72) 0%, rgba(13, 13, 11, 0.52) 45%, rgba(13, 13, 11, 0.92) 100%), url('https://images.unsplash.com/photo-1540541338287-41700207dee6?w=1600&auto=format&fit=crop&q=80'); background-size: cover; background-position: center; background-repeat: no-repeat;"
      ></div>
      <div class="hero-content">
        <p class="hero-eyebrow" style="text-shadow: 0 2px 8px rgba(0,0,0,0.8);">Cartagena de Indias · Marketplace de hospedaje</p>
        <h1 class="hero-title" style="text-shadow: 0 4px 20px rgba(0,0,0,0.85);">Palacio del Mar</h1>
        <p class="hero-sub" style="color: #f5f0e8; text-shadow: 0 2px 10px rgba(0,0,0,0.9); font-weight: 400;">
          Suites de diseño exclusivo entre murallas coloniales, Getsemaní y el mar Caribe.
        </p>
        <div class="hero-actions">
          <button class="btn-primary" (click)="scrollTo('#booking-bar')">Reservar ahora</button>
          <button class="btn-outline" style="background: rgba(13, 13, 11, 0.65); border-color: var(--gold); color: var(--cream); backdrop-filter: blur(4px);" (click)="scrollTo('#habitaciones')">Ver suites</button>
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
    const el = document.querySelector(selector);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
      if (selector === '#booking-bar') {
        setTimeout(() => {
          const target = document.querySelector<HTMLElement>('#booking-bar input, #booking-bar select, #booking-bar button');
          target?.focus();
        }, 600);
      }
    }
  }
}
