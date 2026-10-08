import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Experience } from '../../models/types';

@Component({
  selector: 'app-experiences-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section id="experiences">
      <div class="rooms-header">
        <p class="sec-label">Vive Cartagena</p>
        <h2 class="sec-title">Experiencias exclusivas</h2>
      </div>

      <div class="filter-row" *ngIf="categories.length > 0">
        <button
          type="button"
          class="filter-btn"
          [class.is-active]="selectedCategory === ''"
          (click)="selectedCategory = ''"
        >
          Todas
        </button>
        <button
          *ngFor="let cat of categories"
          type="button"
          class="filter-btn"
          [class.is-active]="selectedCategory === cat"
          (click)="selectedCategory = cat"
        >
          {{ cat | titlecase }}
        </button>
      </div>

      <div class="exp-container" style="max-width: 1200px; margin: 0 auto; position: relative;">
        <div class="exp-grid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 20px;">
          <div class="exp-card" *ngFor="let exp of visibleExperiences">
            <div class="exp-img-wrap">
              <img [src]="exp.mainImage || exp.image || exp.imageUrl || 'https://images.unsplash.com/photo-1533105079780-92b9be482077?w=800'" [alt]="exp.name" loading="lazy" />
              <span class="exp-badge" *ngIf="exp.category">{{ exp.category }}</span>
            </div>
            <div class="exp-content">
              <span class="exp-icon">{{ exp.icon || '✨' }}</span>
              <h3>{{ exp.name || exp.title }}</h3>
              <p>{{ exp.shortDescription || exp.description }}</p>
              <div class="exp-price-row">
                <span class="exp-price">$ {{ exp.price | number:'1.0-0' }}</span>
                <span class="chip" *ngIf="exp.durationHours">{{ exp.durationHours }}h</span>
              </div>
              <button 
                class="exp-btn" 
                [class.exp-btn-added]="isAdded(exp)"
                (click)="toggleExperience(exp)"
              >
                {{ isAdded(exp) ? '✓ Agregada' : 'Agregar' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class ExperiencesSectionComponent implements OnInit {
  experiences: Experience[] = [];
  categories: string[] = [];
  selectedCategory: string = '';
  addedExperiences: Set<string> = new Set();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/experiences').subscribe({
      next: (res) => {
        this.experiences = res.data || res;
        this.categories = Array.from(new Set(this.experiences.map(e => e.category).filter(Boolean))) as string[];
      }
    });
  }

  get visibleExperiences(): Experience[] {
    if (!this.selectedCategory) return this.experiences;
    return this.experiences.filter(e => e.category === this.selectedCategory);
  }

  isAdded(exp: Experience): boolean {
    const id = exp.id || exp._id || '';
    return this.addedExperiences.has(id);
  }

  toggleExperience(exp: Experience): void {
    const id = exp.id || exp._id || '';
    if (this.addedExperiences.has(id)) {
      this.addedExperiences.delete(id);
    } else {
      this.addedExperiences.add(id);
    }
  }
}
