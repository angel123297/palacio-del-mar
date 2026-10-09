import { Component, OnInit, inject, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormGroup, FormControl } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Branch, ApiResponse } from '../../models/types';

@Component({
  selector: 'app-booking-bar',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, MatDatepickerModule, MatNativeDateModule],
  template: `
    <div id="booking-bar" class="bg-[#161311] border-b-2 border-[rgba(201,169,110,0.3)] shadow-2xl sticky top-[57px] z-[190] px-4 md:px-12 relative">
      
      <form class="flex flex-col md:flex-row items-stretch w-full" (ngSubmit)="submit()">
        
        <!-- Fechas (Datepicker Material Oculto, Custom Trigger) -->
        <div class="flex-1 min-w-[240px] p-4 border-b md:border-b-0 md:border-r border-[rgba(201,169,110,0.18)] cursor-pointer hover:bg-[rgba(201,169,110,0.05)] transition relative flex flex-col justify-center group" (click)="picker.open()">
          <label class="text-[10px] tracking-[0.22em] uppercase text-[#c9a96e] font-semibold mb-1">Fechas</label>
          <div class="text-[#f5f0e8] font-serif text-lg md:text-xl font-medium select-none">
            {{ dateRange.value.start ? (dateRange.value.start | date:'dd MMM yyyy') : 'Llegada' }} 
            &nbsp;&mdash;&nbsp; 
            {{ dateRange.value.end ? (dateRange.value.end | date:'dd MMM yyyy') : 'Salida' }}
          </div>
          <span class="text-[11px] text-[rgba(201,169,110,0.7)] mt-1" *ngIf="searchState.nights() > 0">
            {{ searchState.nights() }} noche(s) de estancia
          </span>
          
          <!-- Flexible Pricing Graph Overlay -->
          <div class="absolute left-0 right-0 top-full mt-2 bg-[#161311] border border-[rgba(201,169,110,0.3)] rounded-lg p-3 shadow-2xl opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-50">
            <p class="text-[10px] text-[#c9a96e] uppercase tracking-widest mb-2 text-center">Tendencia de Precios</p>
            <div class="flex items-end justify-between h-12 gap-1">
              <div *ngFor="let h of [40, 60, 30, 80, 50, 40, 90, 70, 40, 30, 50, 60, 40, 30]" 
                   class="w-full bg-[rgba(201,169,110,0.4)] rounded-t-sm"
                   [style.height.%]="h"></div>
            </div>
          </div>

          <!-- Angular Material Datepicker -->
          <mat-date-range-input [rangePicker]="picker" [formGroup]="dateRange" [min]="minDate" style="display: none; visibility: hidden; width: 0; height: 0;">
            <input matStartDate formControlName="start">
            <input matEndDate formControlName="end" (dateChange)="onDatesChanged()">
          </mat-date-range-input>
          <mat-date-range-picker #picker></mat-date-range-picker>
        </div>

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
  minDate = new Date();

  dateRange = new FormGroup({
    start: new FormControl<Date | null>(null),
    end: new FormControl<Date | null>(null)
  });

  constructor() {
    // Sync initial state from service using safe midday parsing
    const f = this.searchState.filters();
    this.dateRange.setValue({
      start: this.searchState.parseToMidday(f.checkIn),
      end: this.searchState.parseToMidday(f.checkOut)
    }, { emitEvent: false });
  }

  ngOnInit(): void {
    // Strongly typed response
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

  onDatesChanged(): void {
    const { start, end } = this.dateRange.value;
    if (start && end) {
      this.searchState.updateDates(start, end);
    }
  }

  updateAdults(val: number) {
    this.searchState.updateGuests(val, this.searchState.filters().children);
  }

  submit(): void {
    this.router.navigate(['/buscar']);
  }
}

