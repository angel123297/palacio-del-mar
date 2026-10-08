import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Suite, Branch } from '../../models/types';
import { BookingModalComponent } from '../booking-modal/booking-modal.component';

@Component({
  selector: 'app-rooms-section',
  standalone: true,
  imports: [CommonModule, RouterModule, BookingModalComponent],
  template: `
    <section id="rooms">
      <div className="rooms-header">
        <p className="sec-label">Alojamiento</p>
        <h2 className="sec-title">Nuestras suites</h2>
        <p className="sec-sub">
          Elige tu zona en Cartagena: murallas coloniales, Getsemaní, Bocagrande o La Boquilla
        </p>
      </div>

      <!-- Sucursales Filter -->
      <div className="filter-row" *ngIf="branches.length > 0">
        <button
          type="button"
          className="filter-btn"
          [class.is-active]="selectedBranch === ''"
          (click)="selectBranch('')"
        >
          Todas las sucursales
        </button>
        <button
          *ngFor="let b of branches"
          type="button"
          className="filter-btn"
          [class.is-active]="selectedBranch === b.slug"
          (click)="selectBranch(b.slug)"
        >
          {{ getShortBranch(b.name) }}
        </button>
      </div>

      <!-- Tipos Filter -->
      <div className="filter-row" *ngIf="types.length > 0" style="margin-top: 10px;">
        <button
          type="button"
          className="filter-btn"
          [class.is-active]="selectedType === ''"
          (click)="selectedType = ''"
        >
          Todas
        </button>
        <button
          *ngFor="let t of types"
          type="button"
          className="filter-btn"
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
      <div className="rooms-grid" *ngIf="!loading && visibleSuites.length > 0">
        <div className="room-card" *ngFor="let suite of visibleSuites">
          <div className="room-img-wrap">
            <img [src]="suite.mainImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800'" [alt]="suite.name" loading="lazy" />
            <span className="room-avail badge-ok" *ngIf="suite.featured">Recomendada</span>
          </div>

          <div className="room-info">
            <p className="room-type">
              {{ suite.type || 'Suite' }}
              <span *ngIf="suite.branch?.name"> · {{ getShortBranch(suite.branch?.name || '') }}</span>
            </p>

            <h3 className="room-name">
              <a [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
                {{ suite.name }}
              </a>
            </h3>

            <div className="room-chips">
              <span className="chip" *ngFor="let amenity of (suite.amenities || []).slice(0, 3)">
                {{ amenity }}
              </span>
              <span className="chip">{{ suite.size || 35 }} m²</span>
              <span className="chip">Hasta {{ suite.maxGuests || 2 }} huéspedes</span>
            </div>

            <div className="room-price">
              <span className="price-old" *ngIf="suite.originalPrice">$ {{ suite.originalPrice | number:'1.0-0' }}</span>
              <strong>$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</strong>
              <span> / noche</span>
            </div>

            <a className="room-detail-link" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
              Ver detalles
            </a>

            <button className="btn-book room-cta" (click)="openBookingModal(suite)">
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
        this.suites = res.data || res;
        this.loading = false;
      },
      error: () => {
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
