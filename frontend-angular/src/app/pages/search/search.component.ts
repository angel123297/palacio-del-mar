import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Suite, ApiResponse } from '../../models/types';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1200px] mx-auto">
        <h1 class="font-serif text-4xl text-[#c9a96e] mb-2">Resultados de Búsqueda</h1>
        <p class="text-[#f5f0e8] opacity-70 mb-10 text-sm tracking-widest uppercase">
          Mostrando disponibilidad para {{ searchState.nights() }} noche(s)
        </p>

        <!-- AI Vibe Matcher Banner -->
        <div *ngIf="searchState.filters().vibe" class="mb-10 p-6 bg-[rgba(201,169,110,0.05)] border border-[rgba(201,169,110,0.4)] rounded-xl flex items-center gap-6">
          <div class="text-4xl">✨</div>
          <div>
            <h2 class="font-serif text-xl text-[#c9a96e] mb-1">Palacio AI Vibe Matcher</h2>
            <p class="text-sm text-[#f5f0e8] opacity-80">Hemos ordenado nuestras suites para maximizar tu experiencia de <strong>{{ getVibeLabel() }}</strong>. Nuestras recomendaciones de IA garantizan que tu estadía será exactamente como la imaginaste.</p>
          </div>
        </div>

        <div *ngIf="loading()" class="flex justify-center items-center py-20 flex-col gap-4">
          <!-- Skeleton loaders -->
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
          <div class="w-full max-w-[800px] h-48 bg-[#1a1a1a] animate-pulse rounded-lg border border-[rgba(201,169,110,0.1)]"></div>
        </div>

        <div *ngIf="!loading() && suites().length === 0" class="text-center py-20 border border-[rgba(201,169,110,0.2)] rounded-lg bg-[rgba(255,255,255,0.02)]">
          <p class="text-xl text-[#f5f0e8] opacity-60">No encontramos suites disponibles para estas fechas.</p>
          <button routerLink="/" class="mt-6 px-6 py-2 bg-transparent border border-[#c9a96e] text-[#c9a96e] hover:bg-[#c9a96e] hover:text-[#0d0d0d] transition duration-300 uppercase tracking-widest text-xs">
            Cambiar Fechas
          </button>
        </div>

        <div *ngIf="!loading() && suites().length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div *ngFor="let suite of suites()" class="group bg-[#111] border border-[rgba(201,169,110,0.15)] rounded-xl overflow-hidden hover:border-[#c9a96e] transition-all duration-500 cursor-pointer flex flex-col" [routerLink]="['/habitaciones', suite.slug || suite.id || suite._id]">
            
            <!-- 360 Virtual Tour Hover Container -->
            <div class="h-64 overflow-hidden relative" style="perspective: 1000px;">
              <div class="w-full h-full relative transition-transform duration-700 ease-out group-hover:[transform:rotateY(10deg)_scale(1.05)]" style="transform-style: preserve-3d;">
                <img [src]="suite.mainImage || 'assets/placeholder.jpg'" alt="{{suite.name}}" class="w-full h-full object-cover opacity-80 group-hover:opacity-100">
              </div>

              <div class="absolute top-4 left-4 bg-[#c9a96e] text-[#0d0d0d] text-xs font-bold px-3 py-1 uppercase tracking-widest rounded-sm z-10 shadow-lg">
                {{ suite.branch?.name || 'Palacio del Mar' }}
              </div>
            </div>

            <div class="p-6 flex flex-col flex-grow">
              <h3 class="font-serif text-2xl text-[#f5f0e8] mb-2">{{ suite.name }}</h3>
              <p class="text-sm text-[#f5f0e8] opacity-60 line-clamp-2 mb-6">{{ suite.description || 'Una experiencia de lujo inigualable.' }}</p>
              
              <div class="mt-auto flex justify-between items-end border-t border-[rgba(255,255,255,0.05)] pt-4">
                <div>
                  <p class="text-[10px] uppercase tracking-[0.2em] text-[#c9a96e] mb-1">Precio Total</p>
                  <p class="font-serif text-2xl text-[#f5f0e8]">$ {{ suite.totalPrice || suite.basePrice || 0 | number:'1.0-0' }}</p>
                </div>
                <button class="px-5 py-2 bg-[#c9a96e] text-[#0d0d0d] uppercase tracking-widest text-xs font-semibold hover:bg-[#e2c99b] transition">
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
  loading = signal(true);

  ngOnInit() {
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

  fetchResults() {
    this.loading.set(true);
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
        
        // AI Vibe Sorting
        const vibe = this.searchState.filters().vibe;
        if (vibe && this.suites().length > 0) {
           const s = [...this.suites()];
           s.sort((a, b) => this.getVibeScore(b, vibe) - this.getVibeScore(a, vibe));
           this.suites.set(s);
        }

        this.loading.set(false);
      },
      error: () => {
        this.suites.set([]);
        this.loading.set(false);
      }
    });
  }

  getVibeScore(suite: any, vibe: string): number {
    let score = 0;
    const name = (suite.name || '').toLowerCase();
    const type = (suite.type || '').toLowerCase();
    const branch = suite.branch ? (suite.branch.name || '').toLowerCase() : '';
    
    if (vibe === 'romantico') {
      if (name.includes('presidencial') || name.includes('bahía') || name.includes('bahia')) score += 10;
      if (type.includes('suite')) score += 5;
    } else if (vibe === 'aventura') {
      if (branch.includes('getsemaní') || branch.includes('getsemani') || branch.includes('boquilla')) score += 10;
      if (name.includes('patio')) score += 5;
    } else if (vibe === 'relax') {
      if (branch.includes('bocagrande') || name.includes('bahía') || name.includes('bahia')) score += 10;
      if (name.includes('premium')) score += 5;
    } else if (vibe === 'fiesta') {
      if (branch.includes('getsemaní') || branch.includes('getsemani') || branch.includes('centro')) score += 10;
      if (suite.maxGuests && suite.maxGuests > 2) score += 5;
    }
    
    const hash = (str: string) => {
      let h = 0;
      for (let i = 0; i < str.length; i++) h = Math.imul(31, h) + str.charCodeAt(i) | 0;
      return h;
    };
    
    score += (Math.abs(hash(suite.id + vibe)) % 100) / 100;
    return score;
  }
}

