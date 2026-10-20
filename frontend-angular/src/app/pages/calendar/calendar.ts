import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
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
  imports: [CommonModule, FormsModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 4rem 1.5rem;">
      <div style="max-width: 1000px; margin: 0 auto;">
        <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); text-align: center; margin-bottom: 2rem;">Calendario de Disponibilidad</h1>
        
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; background: rgba(255,255,255,0.02); padding: 1rem; border-radius: 8px; border: 1px solid rgba(212,175,55,0.2);">
          <button (click)="changeMonth(-1)" class="btn-outline" style="padding: 8px 16px;" [disabled]="isCurrentOrPastMonth" [style.opacity]="isCurrentOrPastMonth ? '0.5' : '1'" [style.cursor]="isCurrentOrPastMonth ? 'not-allowed' : 'pointer'">&larr; Anterior</button>
          <h2 style="margin: 0; color: #fff;">{{ monthName }} {{ year }}</h2>
          <button (click)="changeMonth(1)" class="btn-outline" style="padding: 8px 16px;">Siguiente &rarr;</button>
        </div>

        <div *ngIf="loading" class="luxury-loader-container" style="min-height: 200px;">
          <div class="luxury-spinner"></div>
          <p class="luxury-loader-text">Cargando disponibilidad...</p>
        </div>

        <div *ngIf="!loading && loadError" style="text-align: center; padding: 3rem 1rem; background: rgba(231,76,60,0.05); border: 1px solid rgba(231,76,60,0.3); border-radius: 12px;">
          <p style="color: #ff4d4d; margin: 0 0 1rem 0;">No pudimos consultar la disponibilidad. Revisa tu conexión.</p>
          <button (click)="loadData()" class="btn-outline" style="padding: 8px 16px; cursor: pointer;">Reintentar</button>
        </div>

        <div *ngIf="!loading && !loadError" style="border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; overflow: hidden;">
          <div style="display: grid; grid-template-columns: repeat(7, 1fr); background: rgba(212,175,55,0.1); border-bottom: 1px solid rgba(212,175,55,0.2); text-align: center;">
            <div *ngFor="let d of ['Dom','Lun','Mar','Mié','Jue','Vie','Sáb']" style="padding: 1rem 0; font-weight: bold; color: var(--gold); font-size: 0.9rem; text-transform: uppercase;">{{d}}</div>
          </div>
          
          <div style="display: grid; grid-template-columns: repeat(7, 1fr); text-align: center;">
            <div *ngFor="let empty of emptyDays" style="border-right: 1px solid rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); padding: 1.5rem; background: rgba(0,0,0,0.2);"></div>
            <div *ngFor="let day of calendarDays" 
                 style="border-right: 1px solid rgba(255,255,255,0.05); border-bottom: 1px solid rgba(255,255,255,0.05); padding: 1.5rem; position: relative;"
                 [style.background]="day.isPast ? 'rgba(255, 255, 255, 0.02)' : (day.available ? 'rgba(46, 204, 113, 0.05)' : 'rgba(255, 77, 77, 0.05)')">
              <span style="font-size: 1.2rem; font-weight: 600;" [style.color]="day.isPast ? '#666' : '#fff'">{{ day.date }}</span>
              <div style="margin-top: 0.5rem; font-size: 0.75rem;" [style.color]="day.isPast ? '#666' : (day.available ? '#2ecc71' : '#ff4d4d')">
                {{ day.isPast ? 'Pasado' : (day.available ? 'Disponible' : 'Ocupado') }}
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>
  `
})
export class CalendarComponent implements OnInit {
  currentDate = new Date();
  year = this.currentDate.getFullYear();
  month = this.currentDate.getMonth() + 1; // 1-12
  todayIso = bogotaTodayIso();
  
  loading = false;
  loadError = false;
  availabilityData: any = {};
  
  constructor(private api: ApiService, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
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
        isPast: isPast
      });
    }
    return days;
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
    this.api.get<any>(`/availability/monthly?year=${this.year}&month=${this.month}`).pipe(
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        const data = res.data || res;
        const calendarArray = data.calendar || [];
        this.availabilityData = {};
        calendarArray.forEach((day: any) => {
          this.availabilityData[day.date] = !day.soldOut;
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
