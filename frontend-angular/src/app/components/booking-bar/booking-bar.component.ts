import { Component, OnInit, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Branch, ApiResponse } from '../../models/types';
import { DateRangePickerComponent } from '../date-range-picker.component';

@Component({
  selector: 'app-booking-bar',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, DateRangePickerComponent],
  template: `
    <div id="booking-bar" class="bg-[#161311] border-b-2 border-[rgba(201,169,110,0.3)] shadow-2xl sticky top-[57px] z-[190] px-4 md:px-12 relative">
      
      <form class="flex flex-col md:flex-row items-stretch w-full" (ngSubmit)="submit()">
        
        <!-- Fechas (DateRangePicker) -->
        <app-date-range-picker
          class="flex-[2] min-w-[240px] border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)]"
          [checkIn]="searchState.filters().checkIn"
          [checkOut]="searchState.filters().checkOut"
          [guests]="searchState.filters().adults + searchState.filters().children"
          [branch]="searchState.filters().branchSlug"
          (rangeChange)="onRangeChange($event)">
        </app-date-range-picker>

        <!-- Sucursal -->
        <div class="flex-1 min-w-[140px] p-4 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center" *ngIf="branches.length > 0">
          <label class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Sucursal</label>
          <select [ngModel]="searchState.filters().branchSlug" (ngModelChange)="searchState.updateBranch($event)" name="branch" class="bg-transparent border-none text-[#f5f0e8] font-serif text-lg md:text-xl font-medium outline-none cursor-pointer appearance-none">
            <option value="" class="bg-[#161311] text-[#f5f0e8]">Todas</option>
            <option *ngFor="let b of branches" [value]="b.slug" class="bg-[#161311] text-[#f5f0e8]">
              {{ b.zone || b.name }}
            </option>
          </select>
        </div>

        <!-- Huéspedes -->
        <div class="flex-1 min-w-[120px] p-4 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center">
          <label class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Adultos</label>
          <select [ngModel]="searchState.filters().adults" (ngModelChange)="updateAdults($event)" name="adults" class="bg-transparent border-none text-[#f5f0e8] font-serif text-lg md:text-xl font-medium outline-none cursor-pointer appearance-none">
            <option [ngValue]="1" class="bg-[#161311]">1 adulto</option>
            <option [ngValue]="2" class="bg-[#161311]">2 adultos</option>
            <option [ngValue]="3" class="bg-[#161311]">3 adultos</option>
            <option [ngValue]="4" class="bg-[#161311]">4 adultos</option>
          </select>
        </div>

        <!-- AI Vibe Matcher -->
        <div class="flex-1 min-w-[150px] p-4 hover:bg-[rgba(201,169,110,0.05)] transition flex flex-col justify-center">
          <label class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1 flex items-center gap-1">
            <span>✨ Vibe</span>
          </label>
          <select [ngModel]="searchState.filters().vibe" (ngModelChange)="searchState.updateVibe($event)" name="vibe" class="bg-transparent border-none text-[#f5f0e8] font-serif text-lg md:text-xl font-medium outline-none cursor-pointer appearance-none">
            <option value="" class="bg-[#161311]">Cualquiera</option>
            <option value="romantico" class="bg-[#161311]">Romántico</option>
            <option value="aventura" class="bg-[#161311]">Aventura</option>
            <option value="relax" class="bg-[#161311]">Relax Total</option>
            <option value="fiesta" class="bg-[#161311]">Fiesta</option>
          </select>
        </div>

        <!-- Botón de Búsqueda -->
        <div class="flex items-center justify-center p-0">
          <button type="submit" class="w-full h-full min-h-[60px] bg-[#c9a96e] text-[#0d0d0d] px-8 text-sm tracking-[0.24em] uppercase font-semibold hover:bg-[#e8d5a3] transition-colors whitespace-nowrap">
            Buscar Suites
          </button>
        </div>

      </form>
    </div>
  `
})
export class BookingBarComponent implements OnInit {
  api = inject(ApiService);
  router = inject(Router);
  searchState = inject(SearchStateService);

  branches: Branch[] = [];

  ngOnInit(): void {
    this.api.get<ApiResponse<Branch[]> | Branch[]>('/branches').subscribe({
      next: (res) => {
        if (Array.isArray(res)) {
          this.branches = res;
        } else if (res.data) {
          this.branches = res.data;
        } else {
          this.branches = [];
        }
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

  submit(): void {
    this.router.navigate(['/buscar']);
  }
}

