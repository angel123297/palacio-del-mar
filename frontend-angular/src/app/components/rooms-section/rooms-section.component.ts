import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Suite, Branch } from '../../models/types';

const DEFAULT_FALLBACK_BRANCHES: Branch[] = [
  { id: 'b-bocagrande', name: 'Palacio Bocagrande', slug: 'bocagrande', zone: 'Bocagrande · Mar Caribe' } as Branch,
  { id: 'b-getsemani', name: 'Casona Colonial Getsemaní', slug: 'getsemani', zone: 'Getsemaní · Histórico' } as Branch,
  { id: 'b-centro', name: 'Palacio Imperial San Diego', slug: 'centro-historico', zone: 'Ciudad Amurallada' } as Branch,
  { id: 'b-boquilla', name: 'Reserva Manglares & Sol', slug: 'la-boquilla', zone: 'La Boquilla · Privacidad' } as Branch
];

@Component({
  selector: 'app-rooms-section',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <section id="habitaciones">
      <div class="rooms-header">
        <p class="sec-label">Alojamiento</p>
        <h2 class="sec-title">Nuestras suites</h2>
        <p class="sec-sub">
          Elige tu zona en Cartagena: murallas coloniales, Getsemaní, Bocagrande o La Boquilla
        </p>
      </div>

      <!-- Sucursales Filter -->
      <div class="filter-row" *ngIf="branches.length > 0" role="group" aria-label="Filtrar por sucursal">
        <button
          type="button"
          class="filter-btn"
          [class.is-active]="selectedBranch === ''"
          [attr.aria-pressed]="selectedBranch === ''"
          (click)="selectBranch('')"
        >
          Todas las sucursales
        </button>
        <button
          *ngFor="let b of branches"
          type="button"
          class="filter-btn"
          [class.is-active]="selectedBranch === b.slug"
          [attr.aria-pressed]="selectedBranch === b.slug"
          (click)="selectBranch(b.slug)"
        >
          {{ getShortBranch(b.name) }}
        </button>
      </div>

      <!-- Tipos Filter -->
      <div class="filter-row" *ngIf="types.length > 0" style="margin-top: 10px;" role="group" aria-label="Filtrar por tipo de suite">
        <button
          type="button"
          class="filter-btn"
          [class.is-active]="selectedType === ''"
          [attr.aria-pressed]="selectedType === ''"
          (click)="selectedType = ''"
        >
          Todas
        </button>
        <button
          *ngFor="let t of types"
          type="button"
          class="filter-btn"
          [class.is-active]="selectedType === t.name"
          [attr.aria-pressed]="selectedType === t.name"
          (click)="selectedType = t.name"
        >
          {{ t.name }} ({{ t.count }})
        </button>
      </div>

      <!-- Loading / Empty states -->
      <div *ngIf="loading" class="luxury-loader-container">
        <div class="luxury-spinner"></div>
        <p class="luxury-loader-text">Cargando...</p>
      </div>

      <div *ngIf="!loading && visibleSuites.length === 0" style="text-align: center; padding: 4rem;">
        <p style="color: rgba(245, 240, 232, 0.6);">{{ loadError ? 'No pudimos cargar las suites. Intenta recargar la página.' : 'No hay suites disponibles para los filtros seleccionados.' }}</p>
      </div>

      <!-- Rooms Grid -->
      <div class="rooms-grid" *ngIf="!loading && visibleSuites.length > 0">
        <div class="room-card" *ngFor="let suite of visibleSuites">
          <div class="room-img-wrap">
            <img [src]="getSuiteImage(suite)" (error)="onImageError($event, suite)" [alt]="suite.name" loading="lazy" />
            <span class="room-avail badge-ok" *ngIf="suite.featured">Recomendada</span>
          </div>

          <div class="room-info">
            <p class="room-type">
              {{ suite.type || 'Suite' }}
              <span *ngIf="suite.branch?.name"> · {{ getShortBranch(suite.branch?.name || '') }}</span>
            </p>

            <h3 class="room-name">
              <a [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
                {{ suite.name }}
              </a>
            </h3>

            <div class="room-chips">
              <span class="chip" *ngFor="let amenity of (suite.amenities || []).slice(0, 3)">
                {{ amenity }}
              </span>
              <span class="chip">{{ suite.size || 35 }} m²</span>
              <span class="chip">Hasta {{ suite.maxGuests || 2 }} huéspedes</span>
            </div>

            <div class="room-price">
              <span class="price-old" *ngIf="suite.originalPrice">$ {{ suite.originalPrice | number:'1.0-0' }}</span>
              <strong>$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</strong>
              <span> / noche</span>
            </div>

            <a class="room-detail-link" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]" [attr.aria-label]="'Ver detalles de ' + suite.name">
              Ver detalles
            </a>

            <a class="btn-book room-cta" style="text-decoration: none; display: inline-flex; align-items: center; justify-content: center; text-align: center; min-height: 44px;" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]" [attr.aria-label]="'Reservar ' + suite.name">
              Reservar
            </a>
          </div>
        </div>
      </div>
    </section>
  `
})
export class RoomsSectionComponent implements OnInit {
  private searchState = inject(SearchStateService);
  suites: Suite[] = [];
  branches: Branch[] = [];
  types: any[] = [];
  selectedType: string = '';
  loading = true;
  loadError = false;

  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}

  get selectedBranch(): string {
    return this.searchState.filters().branchSlug;
  }

  set selectedBranch(slug: string) {
    this.searchState.updateBranch(slug);
  }

  ngOnInit(): void {
    this.fetchData();
  }

  fetchData(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        const list: Branch[] = res.data || res || [];
        const filtered = list.filter(b => !this.isJunkBranch(b.name, b.slug));
        this.branches = filtered.length > 0 ? filtered : DEFAULT_FALLBACK_BRANCHES;
        this.cdr.detectChanges();
      },
      error: () => {
        this.branches = DEFAULT_FALLBACK_BRANCHES;
        this.cdr.detectChanges();
      }
    });

    this.api.get<any>('/suites/types').subscribe({
      next: (res) => {
        const tList = res.data || res;
        this.types = Array.isArray(tList) && tList.length > 0 ? tList : [
          { name: 'Presidencial', count: 1 },
          { name: 'Imperial', count: 1 },
          { name: 'Deluxe', count: 1 },
          { name: 'Villa', count: 1 }
        ];
        this.cdr.detectChanges();
      },
      error: () => {
        this.types = [
          { name: 'Presidencial', count: 1 },
          { name: 'Imperial', count: 1 },
          { name: 'Deluxe', count: 1 },
          { name: 'Villa', count: 1 }
        ];
        this.cdr.detectChanges();
      }
    });

    this.api.get<any>('/suites').subscribe({
      next: (res) => {
        const raw = res.data || res;
        const list = Array.isArray(raw) ? raw : (raw.suites || []);
        this.suites = list;
        this.loading = false;
        this.loadError = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.suites = [];
        this.loadError = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  selectBranch(slug: string): void {
    this.selectedBranch = slug;
  }

  get visibleSuites(): Suite[] {
    return this.suites.filter(s => {
      if (this.selectedBranch) {
        const branchObj = typeof s.branch === 'object' && s.branch ? s.branch : this.branches.find(b => b.id === (s.branchId || s.branch || s.propertyId) || b.slug === (s.branchId || s.branch || s.propertyId));
        const bSlug = branchObj?.slug || (typeof s.branch === 'string' ? s.branch : '') || (s.propertyId === this.selectedBranch ? this.selectedBranch : '') || '';
        if (bSlug !== this.selectedBranch && s.propertyId !== this.selectedBranch) return false;
      }
      if (this.selectedType) {
        if (s.type !== this.selectedType) return false;
      }
      return true;
    });
  }

  getShortBranch(name?: string): string {
    if (!name || typeof name !== 'string') return '';
    return name.replace(/^Palacio del Mar\s*·\s*/i, '');
  }

  private isJunkBranch(name?: string, slug?: string): boolean {
    const n = (name || '').trim();
    const s = (slug || '').trim();
    return n.length <= 2 || s.length <= 2;
  }

  getSuiteImage(suite: Suite): string {
    const fallbacks = [
      'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800',
      'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
      'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
      'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800',
      'https://images.unsplash.com/photo-1578683010236-d716f9a3f461?w=800'
    ];
    const brokenIds = ['photo-1591088398332', 'photo-1595526114035', 'photo-1522771739844', 'photo-1566665797739'];
    const raw = suite?.mainImage || '';
    if (!raw || brokenIds.some(id => raw.includes(id))) {
      const seed = (suite?.name || '').length;
      return fallbacks[seed % fallbacks.length];
    }
    return raw;
  }

  onImageError(event: Event, suite?: Suite): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800';
    }
  }
}
