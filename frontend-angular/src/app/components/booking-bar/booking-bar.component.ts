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
          <input type="date" [(ngModel)]="checkIn" name="checkIn" class="bf-input" />
        </div>
        <div class="bf">
          <label class="bf-label">Salida</label>
          <input type="date" [(ngModel)]="checkOut" name="checkOut" class="bf-input" />
        </div>
        <div class="bf" *ngIf="branches.length > 0">
          <label class="bf-label">Sucursal</label>
          <select [(ngModel)]="selectedBranch" name="selectedBranch" class="bf-input">
            <option value="">Todas las sucursales</option>
            <option *ngFor="let b of branches" [value]="b.slug">{{ b.zone || b.name }}</option>
          </select>
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
    .bf-input {
      background: transparent;
      border: none;
      color: var(--cream);
      font-family: 'Jost', sans-serif;
      font-size: 0.85rem;
      outline: none;
      width: 100%;
    }
    .bf-input option {
      background: var(--dark);
      color: var(--cream);
    }
  `]
})
export class BookingBarComponent implements OnInit {
  checkIn: string = '';
  checkOut: string = '';
  selectedBranch: string = '';
  adults: number = 2;
  children: number = 0;
  branches: Branch[] = [];

  constructor(private api: ApiService) {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 2);
    this.checkIn = today.toISOString().split('T')[0];
    this.checkOut = tomorrow.toISOString().split('T')[0];
  }

  ngOnInit(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        this.branches = res.data || res;
      }
    });
  }

  submit(): void {
    document.querySelector('#rooms')?.scrollIntoView({ behavior: 'smooth' });
  }
}
