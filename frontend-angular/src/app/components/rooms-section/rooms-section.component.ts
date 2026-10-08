import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Suite, Branch } from '../../models/types';
import { BookingModalComponent } from '../booking-modal/booking-modal.component';

const FALLBACK_SUITES: Suite[] = [
  {
    id: 's-1',
    _id: 's-1',
    name: 'Superior Patio Colonial',
    slug: 'superior-patio-centro',
    type: 'Habitación',
    description: 'Acogedora habitación con jardín colonial privado, ideal para una estancia relajante.',
    basePrice: 860000,
    originalPrice: 1050000,
    mainImage: 'https://images.unsplash.com/photo-1600210492493-0946911123ea?w=800&auto=format&fit=crop&q=80',
    amenities: ['Jardín privado', 'Aire acondicionado', 'Minibar'],
    size: 42,
    maxGuests: 2,
    featured: false,
    branch: { name: 'Palacio del Mar · Centro Histórico', slug: 'centro-historico' }
  },
  {
    id: 's-2',
    _id: 's-2',
    name: 'Suite Colonial Imperial',
    slug: 'suite-colonial-centro',
    type: 'Suite Deluxe',
    description: 'Suite histórica con patio colonial, arcos originales y tina hidromasaje.',
    basePrice: 1310000,
    originalPrice: 1650000,
    mainImage: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?w=800&auto=format&fit=crop&q=80',
    amenities: ['Patio colonial', 'Tina hidromasaje', 'Balcón'],
    size: 65,
    maxGuests: 2,
    featured: true,
    branch: { name: 'Palacio del Mar · Centro Histórico', slug: 'centro-historico' }
  },
  {
    id: 's-3',
    _id: 's-3',
    name: 'Suite Vista Murallas',
    slug: 'suite-vista-murallas-centro',
    type: 'Suite Premium',
    description: 'Espaciosa suite con vista privilegiada a las murallas coloniales y jacuzzi privado.',
    basePrice: 1720000,
    originalPrice: 2150000,
    mainImage: 'https://images.unsplash.com/photo-1591088398332-8a7791972843?w=800&auto=format&fit=crop&q=80',
    amenities: ['Vista a murallas', 'Jacuzzi', 'Balcón privado'],
    size: 85,
    maxGuests: 2,
    featured: true,
    branch: { name: 'Palacio del Mar · Centro Histórico', slug: 'centro-historico' }
  },
  {
    id: 's-4',
    _id: 's-4',
    name: 'Gran Suite Presidencial',
    slug: 'suite-presidencial-centro',
    type: 'Suite Presidencial',
    description: 'La máxima expresión de lujo colonial. Terraza privada de 80 m² con jacuzzi y servicio de mayordomo.',
    basePrice: 2780000,
    originalPrice: 3440000,
    mainImage: 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&auto=format&fit=crop&q=80',
    amenities: ['Terraza privada', 'Jacuzzi', 'Mayordomo 24/7'],
    size: 180,
    maxGuests: 4,
    featured: true,
    branch: { name: 'Palacio del Mar · Centro Histórico', slug: 'centro-historico' }
  }
];

@Component({
  selector: 'app-rooms-section',
  standalone: true,
  imports: [CommonModule, RouterModule, BookingModalComponent],
  template: `
    <section id="rooms">
      <div class="rooms-header">
        <p class="sec-label">Alojamiento</p>
        <h2 class="sec-title">Nuestras suites</h2>
        <p class="sec-sub">
          Elige tu zona en Cartagena: murallas coloniales, Getsemaní, Bocagrande o La Boquilla
        </p>
      </div>

      <!-- Sucursales Filter -->
      <div class="filter-row" *ngIf="branches.length > 0">
        <button
          type="button"
          class="filter-btn"
          [class.is-active]="selectedBranch === ''"
          (click)="selectBranch('')"
        >
          Todas las sucursales
        </button>
        <button
          *ngFor="let b of branches"
          type="button"
          class="filter-btn"
          [class.is-active]="selectedBranch === b.slug"
          (click)="selectBranch(b.slug)"
        >
          {{ getShortBranch(b.name) }}
        </button>
      </div>

      <!-- Tipos Filter -->
      <div class="filter-row" *ngIf="types.length > 0" style="margin-top: 10px;">
        <button
          type="button"
          class="filter-btn"
          [class.is-active]="selectedType === ''"
          (click)="selectedType = ''"
        >
          Todas
        </button>
        <button
          *ngFor="let t of types"
          type="button"
          class="filter-btn"
          [class.is-active]="selectedType === t.name"
          (click)="selectedType = t.name"
        >
          {{ t.name }} ({{ t.count }})
        </button>
      </div>

      <!-- Loading / Empty states -->
      <div *ngIf="loading" style="text-align: center; padding: 4rem;">
        <p style="color: #c9a96e;">Cargando suites exclusivas…</p>
      </div>

      <div *ngIf="!loading && visibleSuites.length === 0" style="text-align: center; padding: 4rem;">
        <p style="color: rgba(245, 240, 232, 0.6);">No hay suites disponibles para los filtros seleccionados.</p>
      </div>

      <!-- Rooms Grid -->
      <div class="rooms-grid" *ngIf="!loading && visibleSuites.length > 0">
        <div class="room-card" *ngFor="let suite of visibleSuites">
          <div class="room-img-wrap">
            <img [src]="suite.mainImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800'" [alt]="suite.name" loading="lazy" />
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

            <a class="room-detail-link" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
              Ver detalles
            </a>

            <button class="btn-book room-cta" (click)="openBookingModal(suite)">
              Reservar
            </button>
          </div>
        </div>
      </div>
    </section>

    <app-booking-modal [isOpen]="bookingModalOpen" [suite]="selectedSuiteForBooking" (closeEvent)="bookingModalOpen = false"></app-booking-modal>
  `
})
export class RoomsSectionComponent implements OnInit {
  suites: Suite[] = [];
  branches: Branch[] = [];
  types: any[] = [];
  selectedBranch: string = '';
  selectedType: string = '';
  loading = true;

  bookingModalOpen = false;
  selectedSuiteForBooking: Suite | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.fetchData();
  }

  fetchData(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        this.branches = res.data || res;
      }
    });

    this.api.get<any>('/suites/types').subscribe({
      next: (res) => {
        this.types = res.data || res;
      }
    });

    this.api.get<any>('/suites').subscribe({
      next: (res) => {
        const raw = res.data || res;
        const list = Array.isArray(raw) ? raw : (raw.suites || []);
        this.suites = list.length > 0 ? list : FALLBACK_SUITES;
        this.loading = false;
      },
      error: () => {
        this.suites = FALLBACK_SUITES;
        this.loading = false;
      }
    });
  }

  selectBranch(slug: string): void {
    this.selectedBranch = slug;
  }

  get visibleSuites(): Suite[] {
    return this.suites.filter(s => {
      if (this.selectedBranch) {
        const bSlug = s.branch?.slug || '';
        if (bSlug !== this.selectedBranch) return false;
      }
      if (this.selectedType) {
        if (s.type !== this.selectedType) return false;
      }
      return true;
    });
  }

  getShortBranch(name: string): string {
    return name.replace(/^Palacio del Mar\s*·\s*/i, '');
  }

  openBookingModal(suite: Suite): void {
    this.selectedSuiteForBooking = suite;
    this.bookingModalOpen = true;
  }
}
