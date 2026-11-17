import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Branch, ApiResponse } from '../../models/types';
import { DateRangePickerComponent } from '../date-range-picker/date-range-picker.component';

@Component({
  selector: 'app-booking-bar',
  standalone: true,
  imports: [CommonModule, FormsModule, DateRangePickerComponent],
  template: `
    <div id="booking-bar" role="search" aria-label="Buscador de suites y disponibilidad" class="bg-[#161311] border-b-2 border-[rgba(201,169,110,0.3)] shadow-2xl sticky top-[68px] z-[190] px-0 relative w-full">
      <div class="max-w-7xl mx-auto w-full">
        <form class="flex flex-col md:flex-row items-stretch w-full" (ngSubmit)="submit()" aria-label="Filtros de búsqueda de disponibilidad">
          
          <!-- Fechas (DateRangePicker) -->
          <div class="flex-[2] flex min-w-[240px] border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)]">
            <app-date-range-picker
              class="w-full flex"
              [checkIn]="searchState.filters().checkIn"
              [checkOut]="searchState.filters().checkOut"
              [guests]="searchState.filters().adults + searchState.filters().children"
              [branch]="searchState.filters().branchSlug"
              (rangeChange)="onRangeChange($event)">
            </app-date-range-picker>
          </div>

          <!-- Sucursal -->
          <div class="flex-1 min-w-[140px] px-4 py-3 md:py-0 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center">
            <label for="booking-branch" class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Sucursal</label>
            <div class="relative flex items-center w-full">
              <select id="booking-branch" [ngModel]="searchState.filters().branchSlug" (ngModelChange)="searchState.updateBranch($event)" name="branch" aria-label="Seleccionar sucursal de destino" class="booking-select pr-5">
                <option value="" class="bg-[#161311] text-[#f5f0e8]">Todas</option>
                <option *ngFor="let b of branches" [value]="b.slug" class="bg-[#161311] text-[#f5f0e8]">
                  {{ b.zone || b.name }}
                </option>
              </select>
              <svg class="w-3.5 h-3.5 text-[#c9a96e] absolute right-0 pointer-events-none opacity-70" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="6 9 12 15 18 9"></polyline></svg>
            </div>
          </div>

          <!-- Huéspedes / Adultos -->
          <div class="flex-1 min-w-[120px] px-4 py-3 md:py-0 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center">
            <label for="booking-adults" class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Adultos</label>
            <div class="relative flex items-center w-full">
              <select id="booking-adults" [ngModel]="searchState.filters().adults" (ngModelChange)="updateAdults($event)" name="adults" aria-label="Cantidad de adultos acompañantes" class="booking-select pr-5">
                <option [ngValue]="1" class="bg-[#161311]">1 adulto</option>
                <option [ngValue]="2" class="bg-[#161311]">2 adultos</option>
                <option [ngValue]="3" class="bg-[#161311]">3 adultos</option>
                <option [ngValue]="4" class="bg-[#161311]">4 adultos</option>
              </select>
              <svg class="w-3.5 h-3.5 text-[#c9a96e] absolute right-0 pointer-events-none opacity-70" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="6 9 12 15 18 9"></polyline></svg>
            </div>
          </div>

          <!-- Filtro por ambiente / Experiencia -->
          <div class="flex-1 min-w-[140px] px-4 py-3 md:py-0 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center">
            <label for="booking-vibe" class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Experiencia</label>
            <div class="relative flex items-center w-full">
              <select id="booking-vibe" [ngModel]="searchState.filters().vibe" (ngModelChange)="searchState.updateVibe($event)" name="vibe" aria-label="Filtrar por experiencia" class="booking-select pr-5">
                <option value="" class="bg-[#161311]">Cualquiera</option>
                <option value="romantico" class="bg-[#161311]">Romántico</option>
                <option value="aventura" class="bg-[#161311]">Aventura</option>
                <option value="relax" class="bg-[#161311]">Relax Total</option>
                <option value="fiesta" class="bg-[#161311]">Fiesta</option>
              </select>
              <svg class="w-3.5 h-3.5 text-[#c9a96e] absolute right-0 pointer-events-none opacity-70" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><polyline points="6 9 12 15 18 9"></polyline></svg>
            </div>
          </div>

          <!-- Botón de Búsqueda -->
          <div class="flex items-center justify-center p-0">
            <button type="submit" [disabled]="!canSearch" [attr.aria-disabled]="!canSearch" aria-label="Buscar suites y calcular tarifa total" class="w-full h-full min-h-[60px] bg-[#c9a96e] text-[#0d0d0d] px-8 text-sm tracking-[0.24em] uppercase font-semibold hover:bg-[#e8d5a3] transition-colors whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed">
              {{ canSearch ? 'Buscar Suites' : 'Elige tus fechas' }}
            </button>
          </div>

        </form>
      </div>
    </div>
  `,
  styles: [`
    .booking-select,
    select.booking-select {
      width: 100% !important;
      background: transparent !important;
      border: none !important;
      padding: 0 1.25rem 0 0 !important;
      font-family: 'Cormorant Garamond', serif !important;
      font-size: 1.15rem !important;
      color: #f5f0e8 !important;
      outline: none !important;
      box-shadow: none !important;
      border-radius: 0 !important;
      cursor: pointer !important;
      line-height: 1.3 !important;
      letter-spacing: 0.04em !important;
      -webkit-appearance: none !important;
      -moz-appearance: none !important;
      appearance: none !important;
    }
    .booking-select option {
      background-color: #161311 !important;
      color: #f5f0e8 !important;
      font-family: 'Jost', sans-serif !important;
      font-size: 0.95rem !important;
    }
  `]
})
export class BookingBarComponent implements OnInit {
  api = inject(ApiService);
  router = inject(Router);
  searchState = inject(SearchStateService);
  private cdr = inject(ChangeDetectorRef);

  private defaultBranches: Branch[] = [
    { id: 'b-bocagrande', name: 'Palacio Bocagrande', slug: 'bocagrande', zone: 'Bocagrande · Mar Caribe' } as Branch,
    { id: 'b-getsemani', name: 'Casona Colonial Getsemaní', slug: 'getsemani', zone: 'Getsemaní · Histórico' } as Branch,
    { id: 'b-centro', name: 'Palacio Imperial San Diego', slug: 'centro-historico', zone: 'Ciudad Amurallada' } as Branch,
    { id: 'b-boquilla', name: 'Reserva Manglares & Sol', slug: 'la-boquilla', zone: 'La Boquilla · Privacidad' } as Branch
  ];

  branches: Branch[] = [...this.defaultBranches];

  ngOnInit(): void {
    this.api.get<ApiResponse<Branch[]> | Branch[]>('/branches').subscribe({
      next: (res) => {
        const list = Array.isArray(res) ? res : (res?.data || []);
        const filtered = list.filter(b => {
          const n = (b?.name || '').trim();
          return n.length > 2;
        });
        this.branches = filtered.length > 0 ? filtered : this.defaultBranches;
        this.cdr.markForCheck();
      },
      error: () => {
        this.branches = this.defaultBranches;
        this.cdr.markForCheck();
      }
    });
  }

  onRangeChange(event: {checkIn: string, checkOut: string}) {
    if (event.checkIn && event.checkOut) {
      this.searchState.updateDates(
        this.searchState.parseToMidday(event.checkIn),
        this.searchState.parseToMidday(event.checkOut)
      );
    } else if (event.checkIn) {
      // Solo actualizamos el checkIn para mantener el estado
      this.searchState.updateRawDates(event.checkIn, '');
    }
  }

  updateAdults(val: number) {
    this.searchState.updateGuests(val, this.searchState.filters().children);
  }

  get canSearch(): boolean {
    const f = this.searchState.filters();
    return !!(f.checkIn && f.checkOut && f.checkOut > f.checkIn);
  }

  submit(): void {
    // L4: no buscamos sin un rango de fechas válido y completo.
    if (!this.canSearch) return;
    this.router.navigate(['/buscar']);
  }
}

