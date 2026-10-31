import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Experience } from '../../models/types';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-experiences-section',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section id="experiences" class="py-16 px-4 md:px-12 bg-[#0d0d0d] relative overflow-hidden" aria-label="Experiencias Exclusivas en Cartagena">
      <div class="max-w-7xl mx-auto">
        <div class="text-center mb-12">
          <p class="text-[#c9a96e] uppercase tracking-widest text-xs font-semibold mb-2">Momentos Inolvidables</p>
          <h2 class="text-3xl md:text-4xl font-serif text-[#f5f0e8] mb-4">Experiencias de Lujo & Aventura</h2>
          <p class="text-[#aaa] max-w-2xl mx-auto text-sm md:text-base">
            Diseñadas exclusivamente para nuestros huéspedes. Vive la magia colonial, la brisa del Caribe y la gastronomía de Cartagena de Indias.
          </p>
        </div>

        <!-- Skeleton loader -->
        <div *ngIf="loading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div *ngFor="let i of [1,2,3,4]" class="animate-pulse bg-[#161311] border border-[rgba(255,255,255,0.06)] rounded-xl overflow-hidden h-80 flex flex-col">
            <div class="h-44 bg-[rgba(255,255,255,0.05)] w-full"></div>
            <div class="p-4 flex-1 flex flex-col justify-between">
              <div class="space-y-2">
                <div class="h-4 bg-[rgba(255,255,255,0.08)] rounded w-3/4"></div>
                <div class="h-3 bg-[rgba(255,255,255,0.04)] rounded w-full"></div>
              </div>
              <div class="h-4 bg-[rgba(201,169,110,0.15)] rounded w-1/2"></div>
            </div>
          </div>
        </div>

        <!-- Experiences Grid -->
        <div *ngIf="!loading && experiences.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div *ngFor="let exp of experiences" class="group bg-[#161311] border border-[rgba(201,169,110,0.2)] rounded-xl overflow-hidden shadow-lg hover:border-[#c9a96e] transition-all duration-300 flex flex-col">
            <div class="relative h-44 overflow-hidden">
              <img [src]="exp.image || exp.mainImage" [alt]="exp.name" loading="lazy" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
              <span class="absolute top-3 right-3 bg-[rgba(0,0,0,0.7)] text-[#c9a96e] text-[10px] tracking-wider uppercase px-2.5 py-1 rounded-full border border-[rgba(201,169,110,0.4)] backdrop-blur-sm">
                {{ exp.category || 'Exclusivo' }}
              </span>
            </div>
            <div class="p-4 flex-1 flex flex-col justify-between">
              <div>
                <h3 class="text-lg font-serif text-[#f5f0e8] mb-1 group-hover:text-[#c9a96e] transition-colors">{{ exp.name }}</h3>
                <p class="text-xs text-[#888] line-clamp-2 leading-relaxed mb-3">{{ exp.shortDescription || exp.description }}</p>
              </div>
              <div class="pt-3 border-t border-[rgba(255,255,255,0.05)] flex items-center justify-between">
                <div>
                  <span class="text-[10px] text-[#888] uppercase block">Por persona</span>
                  <span class="text-sm font-bold text-[#c9a96e]">$ {{ exp.price | number:'1.0-0' }} COP</span>
                </div>
                <a routerLink="/" fragment="habitaciones" class="text-xs uppercase tracking-wider text-[#f5f0e8] hover:text-[#c9a96e] transition-colors font-medium">
                  Reservar →
                </a>
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

  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.api.get<any>('/experiences').subscribe({
      next: (res) => {
        const list = res.data || res || [];
        this.experiences = Array.isArray(list) ? list.slice(0, 4) : [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }
}
