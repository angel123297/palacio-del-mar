import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Branch } from '../../models/types';

@Component({
  selector: 'app-booking-bar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div id="booking-bar">
      <form class="bf-form" (ngSubmit)="submit()">
        <div class="bf">
          <label class="bf-label">Llegada</label>
          <input
            type="date"
            [(ngModel)]="checkIn"
            (change)="onCheckInChange()"
            [min]="minCheckIn"
            name="checkIn"
            class="bf-input"
          />
          <span class="bf-subtext" *ngIf="checkIn">Check-in desde 3:00 PM</span>
        </div>

        <div class="bf">
          <label class="bf-label">Salida</label>
          <input
            type="date"
            [(ngModel)]="checkOut"
            (change)="onCheckOutChange()"
            [min]="minCheckOut"
            name="checkOut"
            class="bf-input"
          />
          <span class="bf-subtext" *ngIf="nightsCount > 0">{{ nightsCount }} {{ nightsCount === 1 ? 'noche' : 'noches' }} de estancia</span>
        </div>

        <div class="bf" *ngIf="branches.length > 0">
          <label class="bf-label">Sucursal</label>
          <select [(ngModel)]="selectedBranch" name="selectedBranch" class="bf-input">
            <option value="">Todas las sucursales</option>
            <option *ngFor="let b of branches" [value]="b.slug">{{ b.zone || getShortBranch(b.name) }}</option>
          </select>
          <span class="bf-subtext">Cartagena de Indias</span>
        </div>

        <div class="bf">
          <label class="bf-label">Adultos</label>
          <select [(ngModel)]="adults" name="adults" class="bf-input">
            <option [ngValue]="1">1 adulto</option>
            <option [ngValue]="2">2 adultos</option>
            <option [ngValue]="3">3 adultos</option>
            <option [ngValue]="4">4 adultos</option>
          </select>
        </div>

        <div class="bf">
          <label class="bf-label">Niños</label>
          <select [(ngModel)]="children" name="children" class="bf-input">
            <option [ngValue]="0">0 niños</option>
            <option [ngValue]="1">1 niño</option>
            <option [ngValue]="2">2 niños</option>
          </select>
        </div>

        <div class="bf bf-submit">
          <button class="btn-primary bf-submit-btn" type="submit">
            Ver disponibilidad
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    #booking-bar {
      background: #161311;
      border-bottom: 2px solid rgba(201, 169, 110, 0.3);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
      position: sticky;
      top: 57px;
      z-index: 190;
      padding: 0 2rem;
    }
    .bf-form {
      display: flex;
      align-items: stretch;
      width: 100%;
      flex-wrap: wrap;
    }
    .bf {
      flex: 1;
      min-width: 140px;
      padding: 14px 20px;
      border-right: 1px solid rgba(201, 169, 110, 0.18);
      display: flex;
      flex-direction: column;
      justify-content: center;
      transition: background 0.3s ease;
    }
    .bf:hover {
      background: rgba(201, 169, 110, 0.05);
    }
    .bf:last-of-type {
      border-right: none;
    }
    .bf-label {
      font-size: 0.62rem;
      letter-spacing: 0.22em;
      text-transform: uppercase;
      color: var(--gold);
      margin-bottom: 4px;
      font-weight: 600;
    }
    .bf-input {
      background: transparent;
      border: none;
      color: #f5f0e8;
      font-family: 'Cormorant Garamond', serif;
      font-size: 1.05rem;
      font-weight: 500;
      outline: none;
      width: 100%;
      cursor: pointer;
      color-scheme: dark;
    }
    .bf-input::-webkit-calendar-picker-indicator {
      filter: invert(0.85) sepia(100%) hue-rotate(5deg) saturate(200%);
      cursor: pointer;
      opacity: 0.9;
      padding: 2px;
      transition: transform 0.2s ease, opacity 0.2s ease;
    }
    .bf-input::-webkit-calendar-picker-indicator:hover {
      opacity: 1;
      transform: scale(1.15);
    }
    .bf-input option {
      background: #161311;
      color: #f5f0e8;
      font-family: 'Jost', sans-serif;
      padding: 8px;
    }
    .bf-subtext {
      font-size: 0.65rem;
      color: rgba(201, 169, 110, 0.7);
      margin-top: 2px;
    }
    .bf-submit {
      display: flex;
      align-items: center;
      justify-content: center;
      border-right: none !important;
    }
    .bf-submit-btn {
      white-space: nowrap;
      padding: 10px 24px;
      font-size: 0.9rem;
    }
  `]
})
export class BookingBarComponent implements OnInit {
  checkIn: string = '';
  checkOut: string = '';
  minCheckIn: string = '';
  minCheckOut: string = '';
  selectedBranch: string = '';
  adults: number = 2;
  children: number = 0;
  branches: Branch[] = [];

  constructor(private api: ApiService) {
    const today = new Date();
    this.minCheckIn = this.formatDate(today);
    
    this.checkIn = this.minCheckIn;
    this.checkOut = this.addDays(this.checkIn, 2);
    this.minCheckOut = this.addDays(this.checkIn, 1);
  }

  ngOnInit(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        this.branches = res.data || res;
      }
    });
  }

  private formatDate(d: Date): string {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
  }

  private addDays(dateStr: string, days: number): string {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    date.setDate(date.getDate() + days);
    return this.formatDate(date);
  }

  get nightsCount(): number {
    if (!this.checkIn || !this.checkOut) return 0;
    const d1 = new Date(this.checkIn);
    const d2 = new Date(this.checkOut);
    const diff = Math.ceil((d2.getTime() - d1.getTime()) / (1000 * 3600 * 24));
    return diff > 0 ? diff : 1;
  }

  onCheckInChange(): void {
    this.minCheckOut = this.addDays(this.checkIn, 1);
    if (!this.checkOut || this.checkOut <= this.checkIn) {
      this.checkOut = this.addDays(this.checkIn, 2);
    }
  }

  onCheckOutChange(): void {
    if (this.checkOut <= this.checkIn) {
      this.checkIn = this.addDays(this.checkOut, -1);
    }
  }

  getShortBranch(name: string): string {
    return name.replace(/^Palacio del Mar\s*·\s*/i, '');
  }

  submit(): void {
    document.querySelector('#rooms')?.scrollIntoView({ behavior: 'smooth' });
  }
}

