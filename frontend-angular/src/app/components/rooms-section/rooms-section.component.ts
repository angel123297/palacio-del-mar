import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Suite } from '../../models/types';

@Component({
  selector: 'app-rooms-section',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <section id="rooms" style="padding: 4rem 1.5rem; max-width: 1200px; margin: 0 auto;">
      <div className="rooms-header" style="text-align: center; margin-bottom: 3rem;">
        <p style="color: var(--gold); text-transform: uppercase; letter-spacing: 2px; font-weight: 600; margin-bottom: 8px;">Alojamiento de Lujo</p>
        <h2 style="color: #ffffff; font-size: 2.2rem; font-family: var(--font-title);">Nuestras Suites en Cartagena</h2>
      </div>

      <div *ngIf="loading" style="text-align: center; color: var(--gold); padding: 2rem;">
        Cargando suites exclusivas…
      </div>

      <div *ngIf="!loading && suites.length === 0" style="text-align: center; color: #aaa; padding: 2rem;">
        No hay suites disponibles en este momento.
      </div>

      <div *ngIf="!loading && suites.length > 0" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 2rem;">
        <div *ngFor="let suite of suites" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.2); border-radius: 12px; overflow: hidden; display: flex; flex-direction: column;">
          <div style="height: 220px; overflow: hidden; position: relative;">
            <img [src]="suite.mainImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=600'" [alt]="suite.name" style="width: 100%; height: 100%; object-fit: cover;">
          </div>
          <div style="padding: 1.5rem; display: flex; flex-direction: column; flex: 1; justify-content: space-between;">
            <div>
              <p style="color: var(--gold); font-size: 0.85rem; margin-bottom: 4px;">{{suite.type || 'Suite Colonial'}}</p>
              <h3 style="color: #ffffff; margin: 0 0 12px 0; font-size: 1.3rem;">{{suite.name}}</h3>
              <p style="color: #cccccc; font-size: 0.9rem; line-height: 1.5; margin-bottom: 1rem;">{{suite.description}}</p>
            </div>
            <div>
              <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 1rem; margin-top: 1rem;">
                <div>
                  <strong style="color: #ffffff; font-size: 1.3rem;">$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</strong>
                  <span style="color: #888; font-size: 0.85rem;"> / noche</span>
                </div>
                <a [routerLink]="['/habitaciones', suite.id || suite._id || suite.slug]" style="background: var(--gold); color: #0d0d0d; font-weight: 600; padding: 10px 18px; border-radius: 6px; text-decoration: none; font-size: 0.9rem;">Ver Detalle</a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class RoomsSectionComponent implements OnInit {
  suites: Suite[] = [];
  loading = true;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/suites').subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.suites = Array.isArray(raw) ? raw : (raw.suites || []);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
