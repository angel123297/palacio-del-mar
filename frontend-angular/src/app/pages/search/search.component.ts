import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Suite, ApiResponse } from '../../models/types';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1200px] mx-auto">
        <h1 class="font-serif text-4xl text-[#c9a96e] mb-2">Resultados de Búsqueda</h1>
        <p class="text-[#f5f0e8] opacity-70 mb-6 text-sm tracking-widest uppercase">
          Mostrando suites disponibles para {{ searchState.nights() }} noche(s) en Cartagena
        </p>

        <!-- Barra de refinamiento interactivo de búsqueda -->
        <div class="mb-8 p-5 bg-[#141414] border border-[rgba(201,169,110,0.3)] rounded-xl">
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 items-end">
            <!-- Check-in -->
            <div>
              <label for="search-checkin" class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Llegada</label>
              <input id="search-checkin" type="date" [ngModel]="checkInDate()" (ngModelChange)="checkInDate.set($event)" 
                     class="w-full bg-[#0d0d0d] border border-[rgba(201,169,110,0.35)] rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-[#c9a96e]">
            </div>

            <!-- Check-out -->
            <div>
              <label for="search-checkout" class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Salida</label>
              <input id="search-checkout" type="date" [ngModel]="checkOutDate()" (ngModelChange)="checkOutDate.set($event)" 
                     class="w-full bg-[#0d0d0d] border border-[rgba(201,169,110,0.35)] rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-[#c9a96e]">
            </div>

            <!-- Sede / Ubicación -->
            <div>
              <label for="search-branch" class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Sede / Zona</label>
              <select id="search-branch" [ngModel]="selectedBranch()" (ngModelChange)="selectedBranch.set($event)"
                      class="w-full bg-[#0d0d0d] border border-[rgba(201,169,110,0.35)] rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-[#c9a96e]">
                <option value="">Todas las sedes</option>
                <option *ngFor="let b of branches()" [value]="b.slug">{{ b.name }}</option>
              </select>
            </div>

            <!-- Huéspedes -->
            <div>
              <label for="search-guests" class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Huéspedes</label>
              <select id="search-guests" [ngModel]="guestsCount()" (ngModelChange)="guestsCount.set(+$event)"
                      class="w-full bg-[#0d0d0d] border border-[rgba(201,169,110,0.35)] rounded px-3 py-2 text-sm text-white focus:outline-none focus:border-[#c9a96e]">
                <option [value]="1">1 Huésped</option>
                <option [value]="2">2 Huéspedes</option>
                <option [value]="3">3 Huéspedes</option>
                <option [value]="4">4 Huéspedes</option>
                <option [value]="5">5+ Huéspedes</option>
              </select>
            </div>

            <!-- Botón Aplicar -->
            <div>
              <button (click)="applyRefinements()" class="w-full min-h-[40px] bg-[#c9a96e] hover:bg-[#e2c99b] text-[#0d0d0d] font-bold text-xs uppercase tracking-wider rounded transition duration-200 cursor-pointer flex items-center justify-center">
                Actualizar Filtros
              </button>
            </div>
          </div>

          <!-- Filtros activos y ordenar por -->
          <div class="mt-4 pt-4 border-t border-[rgba(255,255,255,0.06)] flex flex-wrap items-center justify-between gap-3 text-xs">
            <div class="flex flex-wrap items-center gap-2">
              <span class="text-[#888]">Filtros activos:</span>
              <span class="px-2.5 py-1 bg-[rgba(201,169,110,0.12)] border border-[rgba(201,169,110,0.3)] rounded-full text-[#c9a96e]">
                📅 {{ searchState.filters().checkIn }} al {{ searchState.filters().checkOut }} ({{ searchState.nights() }}n)
              </span>
              <span class="px-2.5 py-1 bg-[rgba(201,169,110,0.12)] border border-[rgba(201,169,110,0.3)] rounded-full text-[#c9a96e]">
                👥 {{ guestsCount() }} huésped(es)
              </span>
              <span *ngIf="selectedBranch()" class="px-2.5 py-1 bg-[rgba(201,169,110,0.12)] border border-[rgba(201,169,110,0.3)] rounded-full text-[#c9a96e]">
                📍 {{ getBranchName(selectedBranch()) }}
              </span>
              <span *ngIf="searchState.filters().vibe" class="px-2.5 py-1 bg-[rgba(201,169,110,0.12)] border border-[rgba(201,169,110,0.3)] rounded-full text-[#c9a96e]">
                ✨ {{ getVibeLabel() }}
              </span>
            </div>

            <div class="flex items-center gap-2">
              <label for="search-sort" class="text-[#888]">Ordenar por:</label>
              <select id="search-sort" [ngModel]="sortBy()" (ngModelChange)="sortBy.set($event)"
                      class="bg-[#0d0d0d] border border-[rgba(255,255,255,0.15)] rounded px-2.5 py-1 text-xs text-white focus:outline-none focus:border-[#c9a96e]">
                <option value="recommended">Recomendados</option>
                <option value="price_asc">Precio: menor a mayor</option>
                <option value="price_desc">Precio: mayor a menor</option>
                <option value="capacity">Mayor capacidad</option>
              </select>
            </div>
          </div>
        </div>

        <!-- Filtro de ambiente -->
        <div *ngIf="searchState.filters().vibe" class="mb-10 p-6 bg-[rgba(201,169,110,0.05)] border border-[rgba(201,169,110,0.4)] rounded-xl flex items-center gap-6">
          <div class="text-4xl" aria-hidden="true">✨</div>
          <div>
            <h2 class="font-serif text-xl text-[#c9a96e] mb-1">Ordenado por experiencia: {{ getVibeLabel() }}</h2>
            <p class="text-sm text-[#f5f0e8] opacity-80">Priorizamos las suites que mejor combinan con el ambiente <strong>{{ getVibeLabel() }}</strong> para tus fechas.</p>
          </div>
        </div>

        <div *ngIf="loading()" class="flex justify-center items-center py-20 flex-col gap-4">
          <!-- Skeleton loaders -->
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
        </div>

        <!-- Estado de error -->
        <div *ngIf="!loading() && loadError()" class="text-center py-20 border border-[rgba(231,76,60,0.4)] rounded-lg bg-[rgba(231,76,60,0.06)] px-4">
          <p class="text-xl text-[#f5f0e8] mb-2">No pudimos consultar la disponibilidad en este momento.</p>
          <p class="text-sm text-[#f5f0e8] opacity-60 mb-6">Ocurrió un error al conectar con el servidor.</p>
          <button (click)="fetchResults()" class="px-6 py-2.5 bg-[#c9a96e] hover:bg-[#e2c99b] text-[#0d0d0d] font-bold transition duration-300 uppercase tracking-widest text-xs rounded cursor-pointer">
            Reintentar
          </button>
        </div>

        <!-- Estado vacío -->
        <div *ngIf="!loading() && !loadError() && sortedSuites().length === 0" class="text-center py-20 border border-[rgba(201,169,110,0.2)] rounded-lg bg-[rgba(255,255,255,0.02)]">
          <p class="text-xl text-[#f5f0e8] opacity-60">No encontramos suites disponibles para estas fechas o filtros.</p>
          <button (click)="resetFilters()" class="mt-6 px-6 py-2 bg-transparent border border-[#c9a96e] text-[#c9a96e] hover:bg-[#c9a96e] hover:text-[#0d0d0d] transition duration-300 uppercase tracking-widest text-xs cursor-pointer">
            Limpiar Filtros
          </button>
        </div>

        <div *ngIf="!loading() && sortedSuites().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div *ngFor="let suite of sortedSuites()" class="group bg-[#111] border border-[rgba(201,169,110,0.15)] rounded-xl overflow-hidden hover:border-[#c9a96e] transition-all duration-500 cursor-pointer flex flex-col" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
            
            <!-- 360 Virtual Tour Hover Container -->
            <div class="h-64 overflow-hidden relative" style="perspective: 1000px;">
              <div class="w-full h-full relative transition-transform duration-700 ease-out group-hover:[transform:rotateY(10deg)_scale(1.05)]" style="transform-style: preserve-3d;">
                <img [src]="getSuiteImage(suite)" (error)="onImageError($event)" [alt]="suite.name" class="w-full h-full object-cover opacity-80 group-hover:opacity-100" loading="lazy">
              </div>

              <div class="absolute top-4 left-4 bg-[#c9a96e] text-[#0d0d0d] text-xs font-bold px-3 py-1 uppercase tracking-widest rounded-sm z-10 shadow-lg">
                {{ suite.branch?.name || 'Palacio del Mar' }}
              </div>
            </div>

            <div class="p-6 flex flex-col flex-grow">
              <h3 class="font-serif text-2xl text-[#f5f0e8] mb-1">{{ suite.name }}</h3>
              
              <!-- Review and verification badge -->
              <div class="flex items-center gap-2 mb-3 text-xs text-[#f1c40f]">
                <span>★ 4.9</span>
                <span class="text-[#f5f0e8] opacity-60">· Huéspedes verificados</span>
                <span class="text-[#f5f0e8] opacity-40">· Hasta {{ suite.maxGuests || 2 }} pers.</span>
              </div>

              <p class="text-sm text-[#f5f0e8] opacity-60 line-clamp-2 mb-6">{{ suite.description || 'Una experiencia de lujo inigualable.' }}</p>
              
              <div class="mt-auto flex justify-between items-end border-t border-[rgba(255,255,255,0.05)] pt-4">
                <div>
                  <p class="text-[10px] uppercase tracking-[0.2em] text-[#c9a96e] mb-1">
                    {{ suite.totalPrice ? 'Precio Total (' + searchState.nights() + 'n)' : 'Precio desde' }}
                  </p>
                  <p class="font-serif text-2xl text-[#f5f0e8]">$ {{ (suite.totalPrice || suite.basePrice || 0) | number:'1.0-0' }}</p>
                </div>
                <button class="px-5 py-2 bg-[#c9a96e] text-[#0d0d0d] uppercase tracking-widest text-xs font-semibold hover:bg-[#e2c99b] transition cursor-pointer">
                  Reservar
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SearchComponent implements OnInit {
  api = inject(ApiService);
  searchState = inject(SearchStateService);
  
  suites = signal<Suite[]>([]);
  branches = signal<any[]>([]);
  loading = signal(true);
  loadError = signal(false);

  checkInDate = signal<string>('');
  checkOutDate = signal<string>('');
  selectedBranch = signal<string>('');
  guestsCount = signal<number>(2);
  sortBy = signal<'recommended' | 'price_asc' | 'price_desc' | 'capacity'>('recommended');

  sortedSuites = computed(() => {
    const list = [...this.suites()];
    const order = this.sortBy();
    if (order === 'price_asc') {
      list.sort((a, b) => ((a.totalPrice || a.basePrice || 0) - (b.totalPrice || b.basePrice || 0)));
    } else if (order === 'price_desc') {
      list.sort((a, b) => ((b.totalPrice || b.basePrice || 0) - (a.totalPrice || a.basePrice || 0)));
    } else if (order === 'capacity') {
      list.sort((a, b) => ((b.maxGuests || 2) - (a.maxGuests || 2)));
    }
    return list;
  });

  loadFailed(): boolean { return this.loadError(); }

  ngOnInit() {
    const f = this.searchState.filters();
    this.checkInDate.set(f.checkIn);
    this.checkOutDate.set(f.checkOut);
    this.selectedBranch.set(f.branchSlug);
    this.guestsCount.set(f.adults + f.children);

    this.fetchBranches();
    this.fetchResults();
  }

  fetchBranches() {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        const list = res.data || res || [];
        this.branches.set(Array.isArray(list) ? list : []);
      },
      error: () => this.branches.set([])
    });
  }

  getBranchName(slug: string): string {
    const found = this.branches().find(b => b.slug === slug);
    return found ? found.name : slug;
  }

  applyRefinements() {
    this.searchState.updateRawDates(this.checkInDate(), this.checkOutDate());
    this.searchState.updateBranch(this.selectedBranch());
    this.searchState.updateGuests(this.guestsCount(), 0);
    this.fetchResults();
  }

  resetFilters() {
    this.selectedBranch.set('');
    this.searchState.updateBranch('');
    this.fetchResults();
  }

  getVibeLabel(): string {
    const v = this.searchState.filters().vibe;
    const map: Record<string, string> = {
      'romantico': 'Romance',
      'aventura': 'Aventura',
      'relax': 'Relax Total',
      'fiesta': 'Fiesta'
    };
    return map[v] || 'Magia';
  }

  getSuiteImage(suite: Suite): string {
    const fallbacks = [
      'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800',
      'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
      'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
      'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800'
    ];
    const brokenIds = ['photo-1591088398332', 'photo-1595526114035', 'photo-1522771739844', 'photo-1566665797739'];
    const raw = suite?.mainImage || '';
    if (!raw || brokenIds.some(id => raw.includes(id))) {
      const seed = (suite?.name || '').length;
      return fallbacks[seed % fallbacks.length];
    }
    return raw;
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    if (img && !img.dataset['fallbackApplied']) {
      img.dataset['fallbackApplied'] = 'true';
      img.src = 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800';
    }
  }

  fetchResults() {
    this.loading.set(true);
    this.loadError.set(false);
    const params = this.searchState.getApiParams();
    this.api.get<ApiResponse<any> | any>('/availability', params).subscribe({
      next: (res) => {
        const data = res.data || res;
        const available = data.availableSuites || [];
        const mappedSuites = available.map((a: any) => {
          const s = a.suite;
          s.totalPrice = a.totalPrice;
          s.pricePerNight = a.pricePerNight;
          return s;
        });

        this.suites.set(mappedSuites);
        this.loading.set(false);
      },
      error: () => {
        this.suites.set([]);
        this.loadError.set(true);
        this.loading.set(false);
      }
    });
  }
}

