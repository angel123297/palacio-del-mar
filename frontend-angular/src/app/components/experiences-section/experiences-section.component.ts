import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Experience } from '../../models/types';

@Component({
  selector: 'app-experiences-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="experiences" style="padding: 4rem 1.5rem; background: #121212; border-top: 1px solid rgba(212,175,55,0.15);">
      <div style="max-width: 1200px; margin: 0 auto;">
        <div style="text-align: center; margin-bottom: 3rem;">
          <p style="color: var(--gold); text-transform: uppercase; letter-spacing: 2px; font-weight: 600; margin-bottom: 8px;">Vive Cartagena</p>
          <h2 style="color: #ffffff; font-size: 2.2rem; font-family: var(--font-title);">Experiencias Exclusivas</h2>
        </div>

        <div *ngIf="loading" style="text-align: center; color: var(--gold); padding: 2rem;">
          Cargando experiencias…
        </div>

        <div *ngIf="!loading" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 2rem;">
          <div *ngFor="let exp of experiences" style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.2); border-radius: 12px; overflow: hidden;">
            <div style="height: 180px; overflow: hidden; position: relative;">
              <img [src]="exp.mainImage || exp.image || exp.imageUrl || 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=600'" [alt]="exp.name" style="width: 100%; height: 100%; object-fit: cover;">
            </div>
            <div style="padding: 1.25rem;">
              <span style="font-size: 1.5rem; display: block; margin-bottom: 8px;">{{exp.icon || '✨'}}</span>
              <h3 style="color: #ffffff; margin: 0 0 8px 0; font-size: 1.15rem;">{{exp.name || exp.title}}</h3>
              <p style="color: #aaaaaa; font-size: 0.85rem; line-height: 1.5; margin-bottom: 1rem;">{{exp.shortDescription || exp.description}}</p>
              <div style="display: flex; justify-content: space-between; align-items: center; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 10px;">
                <strong style="color: var(--gold); font-size: 1.1rem;">$ {{exp.price | number:'1.0-0'}}</strong>
                <span *ngIf="exp.durationHours" style="color: #888; font-size: 0.8rem;">{{exp.durationHours}}h</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class ExperiencesSectionComponent implements OnInit {
  experiences: Experience[] = [];
  loading = true;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/experiences').subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.experiences = Array.isArray(raw) ? raw : (raw.experiences || []);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
