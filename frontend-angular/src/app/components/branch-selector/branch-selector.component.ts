import { Component, OnInit, Output, EventEmitter, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Branch } from '../../models/types';

const LUXURY_DEFAULT_BRANCHES: Branch[] = [
  {
    id: 'b-bocagrande',
    name: 'Palacio Bocagrande',
    slug: 'bocagrande',
    zone: 'Bocagrande · Mar Caribe',
    tagline: 'Vanguardia arquitectónica con acceso directo a playa y vista panorámica al mar.',
    vibe: ['romantico', 'relax'],
    mainImage: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
    address: 'Cra. 1 # 5-40, Bocagrande',
    city: 'Cartagena de Indias'
  } as Branch,
  {
    id: 'b-getsemani',
    name: 'Casona Colonial Getsemaní',
    slug: 'getsemani',
    zone: 'Getsemaní · Histórico',
    tagline: 'Auténtico encanto colonial del siglo XVIII con patio andaluz y piscina privada.',
    vibe: ['romantico', 'aventura'],
    mainImage: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
    address: 'Calle de la Sierpe # 29-11',
    city: 'Cartagena de Indias'
  } as Branch,
  {
    id: 'b-centro',
    name: 'Palacio Imperial San Diego',
    slug: 'centro-historico',
    zone: 'Ciudad Amurallada',
    tagline: 'Exclusividad amurallada entre plazas coloniales y restaurantes de alta cocina.',
    vibe: ['relax', 'romantico'],
    mainImage: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
    address: 'Calle del Curato # 38-99',
    city: 'Cartagena de Indias'
  } as Branch,
  {
    id: 'b-boquilla',
    name: 'Reserva Manglares & Sol',
    slug: 'la-boquilla',
    zone: 'La Boquilla · Privacidad',
    tagline: 'Desconexión total, brisa marina y villas exclusivas frente al horizonte caribeño.',
    vibe: ['relax', 'aventura'],
    mainImage: 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800',
    address: 'Anillo Vial Km 7',
    city: 'Cartagena de Indias'
  } as Branch
];

@Component({
  selector: 'app-branch-selector',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    :host {
      display: block;
      width: 100%;
    }
    #location {
      display: block !important;
      width: 100% !important;
    }
  `],
  template: `
    <section id="location" class="block w-full py-16 px-4 sm:px-6 lg:px-8 bg-[#0d0d0d] border-b border-[rgba(201,169,110,0.15)] relative overflow-hidden" aria-label="Ubicaciones exclusivas de Palacio del Mar">
      <div class="max-w-7xl mx-auto w-full">
        <div class="text-center mb-12">
          <p class="text-xs uppercase tracking-[0.28em] text-[#c9a96e] font-semibold mb-2">Nuestras Ubicaciones Exclusivas</p>
          <h2 class="text-3xl md:text-5xl font-serif text-[#f5f0e8] font-light">Elige tu Palacio en Cartagena</h2>
          <p class="text-xs sm:text-sm text-[#888] max-w-xl mx-auto mt-2.5">
            Cuatro santuarios independientes diseñados para reconectar con el lujo caribeño y la historia colonial.
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div 
            *ngFor="let branch of branches" 
            class="group relative bg-[#161311] border rounded-2xl overflow-hidden cursor-pointer transition-all duration-300 flex flex-col justify-between"
            [class.border-[#c9a96e]]="activeSlug === branch.slug"
            [class.ring-2]="activeSlug === branch.slug"
            [class.ring-[#c9a96e]/50]="activeSlug === branch.slug"
            [class.shadow-[0_14px_40px_rgba(201,169,110,0.22)]]="activeSlug === branch.slug"
            [class.border-[rgba(201,169,110,0.2)]]="activeSlug !== branch.slug"
            [class.hover:border-[#c9a96e]/70]="activeSlug !== branch.slug"
            [class.hover:-translate-y-1.5]="activeSlug !== branch.slug"
            [class.hover:shadow-[0_12px_32px_rgba(0,0,0,0.6)]]="activeSlug !== branch.slug"
            role="button"
            tabindex="0"
            [attr.aria-pressed]="activeSlug === branch.slug"
            (click)="selectBranch(branch.slug)"
            (keydown.enter)="selectBranch(branch.slug)"
            (keydown.space)="selectBranch(branch.slug); $event.preventDefault()"
          >
            <!-- Card Image -->
            <div class="relative h-48 overflow-hidden">
              <img [src]="branch.mainImage || getFallbackImage(branch.slug)" (error)="onImageError($event, branch.slug)" [alt]="branch.name" loading="lazy" decoding="async" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700 ease-out" />
              <div class="absolute inset-0 bg-gradient-to-t from-[#161311] via-[#161311]/30 to-transparent"></div>
              
              <!-- Zone Badge -->
              <span class="absolute top-3 left-3 bg-[#0d0d0d]/85 backdrop-blur-md text-[#c9a96e] text-[11px] font-medium tracking-wide px-3 py-1 rounded-full border border-[#c9a96e]/35 shadow-md">
                {{ branch.zone }}
              </span>

              <!-- Active Indicator Badge -->
              <span *ngIf="activeSlug === branch.slug" class="absolute top-3 right-3 bg-[#c9a96e] text-[#0d0d0d] text-[10px] uppercase font-bold tracking-wider px-2.5 py-0.5 rounded-full shadow-lg flex items-center gap-1">
                <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
                Activo
              </span>
            </div>

            <!-- Card Content -->
            <div class="p-5 flex-1 flex flex-col justify-between">
              <div>
                <h3 class="font-serif text-lg md:text-xl text-[#f5f0e8] group-hover:text-[#c9a96e] transition-colors mb-2 font-medium leading-snug">
                  {{ branch.name }}
                </h3>
                <p class="text-xs text-[#a0a0a0] leading-relaxed mb-4 line-clamp-2">
                  {{ branch.tagline }}
                </p>
              </div>

              <div>
                <div class="flex flex-wrap gap-1.5 mb-4">
                  <span *ngFor="let v of branch.vibe" class="inline-flex items-center text-[10px] uppercase tracking-wider font-semibold text-[#c9a96e] bg-[rgba(201,169,110,0.08)] border border-[rgba(201,169,110,0.22)] px-2.5 py-0.5 rounded-full">
                    {{ formatVibe(v) }}
                  </span>
                </div>

                <div class="pt-3 border-t border-[rgba(201,169,110,0.12)] flex items-center justify-between text-xs text-[#c9a96e] font-medium">
                  <span>{{ activeSlug === branch.slug ? 'Sede seleccionada' : 'Ver suites en esta sede' }}</span>
                  <span class="group-hover:translate-x-1 transition-transform">→</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `
})
export class BranchSelectorComponent implements OnInit {
  private searchState = inject(SearchStateService);
  branches: Branch[] = [...LUXURY_DEFAULT_BRANCHES];

  @Output() branchSelected = new EventEmitter<string>();

  constructor(private api: ApiService) {}

  get activeSlug(): string {
    return this.searchState.filters().branchSlug;
  }

  formatVibe(vibe: string): string {
    const map: Record<string, string> = {
      romantico: 'Romántico',
      relax: 'Relax Total',
      aventura: 'Aventura',
      fiesta: 'Celebración'
    };
    return map[(vibe || '').toLowerCase()] || vibe;
  }

  ngOnInit(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        const list: Branch[] = res.data || res || [];
        const filtered = list.filter(b => {
          const n = (b?.name || '').trim();
          const s = (b?.slug || '').trim();
          return n.length > 2 && s.length > 2;
        });
        this.branches = filtered.length > 0 ? filtered : LUXURY_DEFAULT_BRANCHES;
      },
      error: () => {
        this.branches = LUXURY_DEFAULT_BRANCHES;
      }
    });
  }

  onImageError(event: Event, slug: string): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = this.getFallbackImage(slug);
    }
  }

  selectBranch(slug: string): void {
    const next = this.activeSlug === slug ? '' : slug;
    this.searchState.updateBranch(next);
    this.branchSelected.emit(next);
    document.querySelector('#habitaciones')?.scrollIntoView({ behavior: 'smooth' });
  }

  getFallbackImage(slug: string): string {
    const images: Record<string, string> = {
      'centro-historico': 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
      'getsemani': 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'bocagrande': 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
      'la-boquilla': 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800'
    };
    return images[slug] || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800';
  }
}
