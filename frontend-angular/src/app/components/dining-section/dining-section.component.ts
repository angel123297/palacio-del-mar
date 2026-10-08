import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dining-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="dining">
      <div class="dining-content">
        <p class="sec-label">Gastronomía</p>
        <h2 class="sec-title">Gastronomía de autor</h2>
        <p>
          Nuestro chef ejecutivo reinterpreta la cocina caribeña con producto local: pesca del
          día, coco, plátano y especias del Caribe colombiano, servidos en el patio central bajo
          las estrellas.
        </p>
        <div class="dining-items">
          <div class="dining-item" *ngFor="let d of dishes">
            <span class="dining-num">{{ d.num }}</span>
            <div>
              <strong>{{ d.name }} <span style="color: var(--gold); margin-left: 8px; font-size: 0.9rem;">{{ d.price }}</span></strong>
              <p>{{ d.desc }}</p>
            </div>
          </div>
        </div>
        <button
          type="button"
          class="btn-outline"
          style="margin-top: 1.5rem;"
          (click)="showMenu = true"
        >
          📖 Ver Menú Completo & Carta de Vinos
        </button>
      </div>

      <div class="dining-img">
        <img
          src="https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=900&auto=format&fit=crop&q=80"
          alt="Restaurante de Palacio del Mar"
          loading="lazy"
        />
      </div>

      <div class="modal-backdrop" *ngIf="showMenu" (click)="showMenu = false" style="position: fixed; inset: 0; background: rgba(0,0,0,0.8); z-index: 1000; display: flex; align-items: center; justify-content: center;">
        <div class="modal-content" (click)="$event.stopPropagation()" style="background: var(--dark); border: 1px solid var(--gold); max-width: 650px; padding: 2rem; border-radius: 8px; width: 90%;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; border-bottom: 1px solid rgba(212,175,55,0.3); padding-bottom: 1rem;">
            <h3 style="margin: 0; color: var(--gold);">🍽️ Carta del Restaurante Palacio del Mar</h3>
            <button (click)="showMenu = false" style="background: none; border: none; color: #fff; font-size: 1.5rem; cursor: pointer;">×</button>
          </div>
          <div *ngFor="let section of extraMenu" style="margin-bottom: 1.5rem;">
            <h4 style="color: #ffffff; margin-bottom: 0.5rem; border-bottom: 1px dashed rgba(255,255,255,0.1); padding-bottom: 4px;">
              {{ section.category }}
            </h4>
            <ul style="list-style: none; padding: 0; margin: 0;">
              <li *ngFor="let item of section.items" style="color: #cccccc; padding: 6px 0; font-size: 0.95rem;">{{ item }}</li>
            </ul>
          </div>
          <div style="text-align: center; margin-top: 1.5rem;">
            <button class="btn-primary" (click)="showMenu = false">Cerrar Menú</button>
          </div>
        </div>
      </div>
    </section>
  `
})
export class DiningSectionComponent {
  showMenu = false;

  dishes = [
    { num: '01', name: 'Ceviche de camarón al coco', desc: 'Camarón fresco, leche de coco, ají dulce y plátano verde.', price: '$ 68.000' },
    { num: '02', name: 'Arroz de mariscos del Caribe', desc: 'Arroz meloso con langosta, camarón y calamar de la bahía.', price: '$ 125.000' },
    { num: '03', name: 'Posta cartagenera', desc: 'Carne braseada en salsa de panela, vino tinto y especias.', price: '$ 94.000' },
    { num: '04', name: 'Cocada horneada', desc: 'Coco caramelizado con helado de maracuyá.', price: '$ 38.000' }
  ];

  extraMenu = [
    { category: 'Entradas', items: ['Tartar de atún con mango biche - $ 72.000', 'Carpaccio de pulpo al maracuyá - $ 65.000', 'Empanaditas de jaiba y suero costeño - $ 48.000'] },
    { category: 'Fuertes', items: ['Pescado frito entero con arroz de coco - $ 110.000', 'Mero en salsa de cazuela caribeña - $ 135.000', 'Lomo al trapo con mantequilla de corozo - $ 120.000'] },
    { category: 'Cócteles de Autor', items: ['Cartagena Mule (Ron añejo, jengibre, maracuyá) - $ 45.000', 'Mojito de Corozo - $ 42.000', 'Gin Tonic Caribeño - $ 48.000'] }
  ];
}
