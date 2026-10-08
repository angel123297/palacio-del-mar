import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-testimonials-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="testimonials" style="padding: 4rem 1.5rem; background-color: #121212; border-top: 1px solid rgba(212,175,55,0.15);">
      <div className="rooms-header" style="text-align: center; margin-bottom: 3rem;">
        <p className="sec-label">Experiencias inolvidables</p>
        <h2 className="sec-title">Lo que dicen nuestros huéspedes</h2>
        <p className="sec-sub">Calificación promedio 9.8/10 basada en más de 450 reseñas verificadas</p>
      </div>

      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; max-width: 1200px; margin: 0 auto;">
        <div
          *ngFor="let item of testimonials"
          style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.2); border-radius: 12px; padding: 2rem; display: flex; flex-direction: column; justify-content: space-between;"
        >
          <div>
            <div style="color: var(--gold); font-size: 1.2rem; margin-bottom: 0.75rem;">
              ★★★★★
            </div>
            <p style="font-style: italic; color: #e0e0e0; line-height: 1.6; margin-bottom: 1.5rem;">
              "{{ item.text }}"
            </p>
          </div>
          <div style="border-top: 1px solid rgba(255, 255, 255, 0.08); padding-top: 1rem;">
            <strong style="display: block; color: #ffffff;">{{ item.name }}</strong>
            <span style="font-size: 0.85rem; color: #a0a0a0; display: block;">{{ item.location }}</span>
            <span style="font-size: 0.8rem; color: var(--gold); display: block; margin-top: 4px;">{{ item.suite }}</span>
          </div>
        </div>
      </div>
    </section>
  `
})
export class TestimonialsSectionComponent {
  testimonials = [
    {
      id: 1,
      name: 'Carolina & Mateo R.',
      location: 'Bogotá, Colombia',
      text: 'Nuestra estancia en la Suite Presidencial del Centro Histórico fue inolvidable. La atención al detalle, el desayuno en el patio colonial y la vista a las murallas superaron todas nuestras expectativas.',
      suite: 'Suite Presidencial · Centro Histórico'
    },
    {
      id: 2,
      name: 'Jean-Luc Dubois',
      location: 'París, Francia',
      text: 'Un auténtico palacio del siglo XVII equipado con el mejor lujo moderno. La excursión en yate a las Islas del Rosario gestionada desde la recepción fue el momento cumbre de nuestro viaje.',
      suite: 'Suite Deluxe · Getsemaní'
    },
    {
      id: 3,
      name: 'Sofia & David Miller',
      location: 'Miami, EE. UU.',
      text: 'El spa caribeño con masajes de barro volcánico y el restaurante de autor son sencillamente excepcionales. El equipo de Concierge estuvo atento a cada requerimiento.',
      suite: 'Suite Imperial · Centro Histórico'
    }
  ];
}
