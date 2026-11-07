import { Component, OnInit, ChangeDetectorRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { Suite } from '../../models/types';
import { finalize } from 'rxjs';

const bogotaTodayIso = (): string => {
  // M7: "hoy" en la zona horaria del negocio (America/Bogotá), igual que el resto de la app.
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  } catch {
    const t = new Date();
    return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
  }
};

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  styles: [`
    #calendar-suite-select:focus-visible {
      outline: 2px solid var(--gold) !important;
      outline-offset: 2px;
      border-color: var(--gold) !important;
    }
  `],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 4rem 1.5rem;">
      <div style="max-width: 1000px; margin: 0 auto;">
        <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); text-align: center; margin-bottom: 0.5rem;">Calendario de Disponibilidad</h1>
        <p style="text-align: center; color: rgba(245,240,232,0.7); font-size: 0.95rem; margin-bottom: 2rem;">
          Consulta la disponibilidad por suite o en todo el catálogo y haz clic en cualquier fecha libre para iniciar tu reserva.
        </p>

        <!-- Selector de Suite -->
        <div style="display: flex; flex-wrap: wrap; gap: 1rem; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; background: rgba(255,255,255,0.03); padding: 1rem 1.25rem; border-radius: 8px; border: 1px solid rgba(212,175,55,0.25);">
          <div style="display: flex; align-items: center; gap: 0.75rem; flex: 1; min-width: 260px;">
            <label for="calendar-suite-select" style="color: var(--gold); font-size: 0.85rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; white-space: nowrap;">Filtrar Suite:</label>
            <select id="calendar-suite-select" [(ngModel)]="selectedSuiteId" (ngModelChange)="loadData()" style="flex: 1; background: #141414; color: #f5f0e8; border: 1px solid rgba(212,175,55,0.35); border-radius: 6px; padding: 8px 12px; font-size: 0.9rem;">
              <option value="">Todas las Suites ({{ suites.length }} alojamientos)</option>
              <option *ngFor="let s of suites" [value]="s.id || s._id">{{ s.name }} — $ {{ (s.basePrice || 450000) | number:'1.0-0' }} / noche</option>
            </select>
          </div>
          <div style="display: flex; gap: 1rem; font-size: 0.8rem; color: #ccc;">
            <span style="display: flex; align-items: center; gap: 6px;"><span style="width: 10px; height: 10px; border-radius: 50%; background: #2ecc71; display: inline-block;"></span> Disponible (clic para reservar)</span>
            <span style="display: flex; align-items: center; gap: 6px;"><span style="width: 10px; height: 10px; border-radius: 50%; background: #ff4d4d; display: inline-block;"></span> Ocupado</span>
          </div>
        </div>
        
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; background: rgba(255,255,255,0.02); padding: 1rem; border-radius: 8px; border: 1px solid rgba(212,175,55,0.2);">
          <button (click)="changeMonth(-1)" class="btn-outline" style="padding: 8px 16px;" [disabled]="isCurrentOrPastMonth" [style.opacity]="isCurrentOrPastMonth ? '0.5' : '1'" [style.cursor]="isCurrentOrPastMonth ? 'not-allowed' : 'pointer'">&larr; Anterior</button>
          <h2 style="margin: 0; color: #fff;">{{ monthName }} {{ year }}</h2>
          <button (click)="changeMonth(1)" class="btn-outline" style="padding: 8px 16px;">Siguiente &rarr;</button>
        </div>

        <div *ngIf="loading" class="luxury-loader-container" style="min-height: 200px;">
          <div class="luxury-spinner"></div>
          <p class="luxury-loader-text">Cargando disponibilidad...</p>
        </div>

        <div *ngIf="!loading && loadError" role="alert" style="text-align: center; padding: 3rem 1.5rem; background: rgba(231,76,60,0.06); border: 1px solid rgba(231,76,60,0.3); border-radius: 12px;">
          <p style="color: #ff9999; margin: 0 0 1.2rem 0; font-size: 1rem; line-height: 1.5;">Estamos sincronizando las suites más exclusivas para tus fechas. Por favor reintenta en un momento.</p>
          <button (click)="loadData()" class="btn-outline" style="min-height: 44px; padding: 10px 24px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center;">Reintentar</button>
        </div>

        <div *ngIf="!loading && !loadError" style="border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; overflow: hidden;">
          <div style="display: grid; grid-template-columns: repeat(7, 1fr); background: rgba(212,175,55,0.1); border-bottom: 1px solid rgba(212,175,55,0.2); text-align: center;">
            <div *ngFor="let d of ['Dom','Lun','Mar','Mié','Jue','Vie','Sáb']" style="padding: 1rem 0; font-weight: bold; color: var(--gold); font-size: 0.9rem; text-transform: uppercase;">{{d}}</div>
          </div>
          
          <div style="display: grid; grid-template-columns: repeat(7, 1fr); text-align: center;">
            <div *ngFor="let empty of emptyDays" style="border-right: 1px solid rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); padding: 1.5rem; background: rgba(0,0,0,0.2);"></div>
            <button *ngFor="let day of calendarDays"
                 type="button"
                 [disabled]="day.isPast || !day.available"
                 (click)="selectDayForBooking(day)"
                 style="border: none; border-right: 1px solid rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); padding: 1.25rem 0.5rem; position: relative; transition: all 0.2s;"
                 [style.cursor]="(!day.isPast && day.available) ? 'pointer' : 'default'"
                 [style.background]="day.isPast ? 'rgba(255, 255, 255, 0.02)' : (day.available ? 'rgba(46, 204, 113, 0.06)' : 'rgba(255, 77, 77, 0.06)')">
              <span style="font-size: 1.2rem; font-weight: 600; display: block;" [style.color]="day.isPast ? '#666' : '#fff'">{{ day.date }}</span>
              <div style="margin-top: 0.4rem; font-size: 0.72rem; font-weight: 600;" [style.color]="day.isPast ? '#666' : (day.available ? '#2ecc71' : '#ff4d4d')">
                {{ day.isPast ? 'Pasado' : (day.available ? (day.availableSuites ? day.availableSuites + ' disp.' : 'Disponible') : 'Ocupado') }}
              </div>
              <div *ngIf="!day.isPast && day.available" style="margin-top: 0.25rem; font-size: 0.65rem; color: var(--gold); text-transform: uppercase; letter-spacing: 0.5px;">
                Reservar &rarr;
              </div>
            </button>
          </div>
        </div>

      </div>
    </div>
  `
})
export class CalendarComponent implements OnInit {
  private router = inject(Router);
  private searchState = inject(SearchStateService);

  currentDate = new Date();
  year = this.currentDate.getFullYear();
  month = this.currentDate.getMonth() + 1; // 1-12
  todayIso = bogotaTodayIso();
  
  loading = false;
  loadError = false;
  availabilityData: any = {};
  availableSuitesByDate: Record<string, number> = {};
  suites: Suite[] = [];
  selectedSuiteId = '';
  
  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.api.get<any>('/suites').subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.suites = Array.isArray(raw) ? raw : (raw.suites || []);
        this.cdr.detectChanges();
      }
    });
    this.loadData();
  }

  get monthName(): string {
    const months = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
    return months[this.month - 1];
  }

  get emptyDays(): number[] {
    const firstDay = new Date(this.year, this.month - 1, 1).getDay();
    return Array(firstDay).fill(0);
  }

  get isCurrentOrPastMonth(): boolean {
    const currentYear = Number(this.todayIso.slice(0, 4));
    const currentMonth = Number(this.todayIso.slice(5, 7));
    if (this.year < currentYear) return true;
    if (this.year === currentYear && this.month <= currentMonth) return true;
    return false;
  }

  get calendarDays(): any[] {
    const daysInMonth = new Date(this.year, this.month, 0).getDate();
    const days = [];
    const todayStr = this.todayIso;

    for (let i = 1; i <= daysInMonth; i++) {
      const dateStr = `${this.year}-${String(this.month).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
      const isPast = dateStr < todayStr;
      days.push({
        date: i,
        dateStr,
        available: isPast ? false : (this.availabilityData[dateStr] === true),
        availableSuites: this.availableSuitesByDate[dateStr],
        isPast: isPast
      });
    }
    return days;
  }

  selectDayForBooking(day: any) {
    if (!day || day.isPast || !day.available) return;
    const [y, m, d] = day.dateStr.split('-').map(Number);
    const checkOutIso = new Date(Date.UTC(y, m - 1, d + 2)).toISOString().slice(0, 10);
    this.searchState.updateRawDates(day.dateStr, checkOutIso);
    if (this.selectedSuiteId) {
      const target = this.suites.find(s => (s.id || s._id) === this.selectedSuiteId);
      this.router.navigate(['/habitaciones', target?.slug || this.selectedSuiteId]);
    } else {
      this.router.navigate(['/buscar']);
    }
  }

  changeMonth(dir: number) {
    let d = new Date(this.year, this.month - 1 + dir, 1);
    this.year = d.getFullYear();
    this.month = d.getMonth() + 1;
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.loadError = false;
    const params: any = { year: this.year, month: this.month };
    if (this.selectedSuiteId) params.suiteId = this.selectedSuiteId;
    this.api.get<any>('/availability/monthly', params).pipe(
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        const data = res.data || res;
        const calendarArray = data.calendar || [];
        this.availabilityData = {};
        this.availableSuitesByDate = {};
        calendarArray.forEach((day: any) => {
          this.availabilityData[day.date] = !day.soldOut;
          if (day.availableSuites !== undefined) {
            this.availableSuitesByDate[day.date] = day.availableSuites;
          }
        });
        this.cdr.detectChanges();
      },
      error: () => {
        // C6: ante error mostramos estado de fallo real; nunca "todo ocupado".
        this.availabilityData = {};
        this.loadError = true;
        this.cdr.detectChanges();
      }
    });
  }
}
