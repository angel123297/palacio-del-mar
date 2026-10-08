import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-spa-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="spa">
      <div
        className="spa-bg"
        style="background-image: url('https://images.unsplash.com/photo-1544161515-4ab6ce6db874?w=1400&auto=format&fit=crop&q=80')"
      ></div>
      <div className="spa-content">
        <p className="spa-tag">Bienestar</p>
        <h2 className="sec-title">Spa Caribe</h2>
        <p>
          Tratamientos con ingredientes autóctonos del Caribe colombiano: barro volcánico,
          coco, tabaco y ron añejo. Circuito de aguas con vista a la bahía y masajes
          inspirados en los ritmos de Palenque.
        </p>
        <div className="spa-tags">
          <span className="perk-tag" *ngFor="let t of tags">{{ t }}</span>
        </div>
        <button
          type="button"
          className="btn-outline"
          style="margin-top: 1.5rem; border-color: #ffffff; color: #ffffff;"
          (click)="showSpaModal = true"
        >
          🌿 Ver Menú de Tratamientos Spa
        </button>
      </div>

      <div className="modal-backdrop" *ngIf="showSpaModal" (click)="showSpaModal = false" style="position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 1000; display: flex; align-items: center; justify-content: center;">
        <div className="modal-content" (click)="$event.stopPropagation()" style="background: var(--dark); border: 1px solid var(--gold); max-width: 650px; padding: 2rem; border-radius: 8px; width: 90%;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid rgba(212,175,55,0.3); padding-bottom: 1rem;">
            <h3 style="margin: 0; color: var(--gold);">🌺 Carta de Servicios & Tratamientos Spa Caribe</h3>
            <button (click)="showSpaModal = false" style="background: none; border: none; color: #fff; font-size: 1.5rem; cursor: pointer;">×</button>
          </div>
          <div style="display: flex; flex-direction: column; gap: 1.25rem;">
            <div *ngFor="let t of spaTreatments" style="border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 1rem;">
              <div style="display: flex; justify-content: space-between; color: #ffffff; font-weight: 600;">
                <span>{{ t.name }}</span>
                <span style="color: var(--gold);">{{ t.price }}</span>
              </div>
              <p style="color: #aaaaaa; font-size: 0.9rem; margin: 4px 0 0 0;">{{ t.desc }}</p>
            </div>
          </div>
          <div style="text-align: center; margin-top: 1.5rem;">
            <button className="btn-primary" (click)="showSpaModal = false">Cerrar Menú</button>
          </div>
        </div>
      </div>
    </section>
  `
})
export class SpaSectionComponent {
  showSpaModal = false;

  tags = ['Baños de barro', 'Ritmos caribeños', 'Aceites autóctonos', 'Terapia de piedras'];

  spaTreatments = [
    { name: 'Ritual Caribeño Completo (90 min)', desc: 'Exfoliación con sal marina y coco, masaje corporal relajante y mascarilla facial de barro volcánico del Totumo.', price: '$ 320.000' },
    { name: 'Masaje de Piedras Volcánicas (60 min)', desc: 'Terapia térmica relajante con piedras de lava y aceites autóctonos de jazmín y ylang-ylang.', price: '$ 240.000' },
    { name: 'Hidroterapia & Jacuzzi Privado (45 min)', desc: 'Circuito de aguas aromatizadas con eucalipto y vista panorámica a la bahía amurallada.', price: '$ 180.000' },
    { name: 'Masaje en Pareja frente al Mar (75 min)', desc: 'Sesión de relajación para dos personas en pérgola privada con copa de champán.', price: '$ 490.000' }
  ];
}
