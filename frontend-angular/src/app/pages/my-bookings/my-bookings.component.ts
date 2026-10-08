import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Booking } from '../../models/types';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 3rem 1.5rem; max-width: 1100px; margin: 0 auto;">
      <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); margin-bottom: 2rem;">Mis Reservas</h1>

      <div *ngIf="loading" style="text-align: center; color: var(--gold); padding: 3rem;">
        Cargando tus reservas…
      </div>

      <div *ngIf="!loading && bookings.length === 0" style="text-align: center; padding: 4rem 1rem; background: rgba(255,255,255,0.02); border-radius: 12px; border: 1px solid rgba(212,175,55,0.2);">
        <p style="color: #aaa; font-size: 1.1rem; margin-bottom: 1.5rem;">No tienes reservas registradas en tu cuenta.</p>
        <a routerLink="/" style="background: var(--gold); color: #0d0d0d; font-weight: 700; padding: 12px 24px; border-radius: 6px; text-decoration: none;">Explorar Suites</a>
      </div>

      <div *ngIf="!loading && bookings.length > 0" style="display: flex; flex-direction: column; gap: 1.5rem;">
        <div *ngFor="let booking of bookings" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 1.5rem; display: flex; flex-wrap: wrap; gap: 1.5rem; justify-content: space-between; align-items: center;">
          <div>
            <h3 style="margin: 0 0 6px 0; color: #ffffff;">{{ booking.suite?.name || 'Suite Palacio del Mar' }}</h3>
            <p style="color: #aaa; font-size: 0.85rem; margin: 0 0 10px 0;">Código: {{ booking.bookingCode || booking.id || booking._id }}</p>
            <div style="display: flex; gap: 1.5rem; font-size: 0.95rem; color: #ddd;">
              <span><strong>Check-in:</strong> {{ booking.checkIn | date:'shortDate' }}</span>
              <span><strong>Check-out:</strong> {{ booking.checkOut | date:'shortDate' }}</span>
            </div>
          </div>

          <div style="text-align: right;">
            <div style="font-size: 1.4rem; font-weight: 700; color: var(--gold); margin-bottom: 6px;">$ {{ booking.totalPrice | number:'1.0-0' }}</div>
            <span style="display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; text-transform: uppercase;"
                  [style.background]="booking.status === 'confirmed' ? 'rgba(46, 204, 113, 0.2)' : 'rgba(241, 196, 15, 0.2)'"
                  [style.color]="booking.status === 'confirmed' ? '#2ecc71' : '#f1c40f'">
              {{ booking.status }}
            </span>
          </div>
        </div>
      </div>
    </div>
  `
})
export class MyBookingsComponent implements OnInit {
  bookings: Booking[] = [];
  loading = true;

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    if (!this.auth.getToken()) {
      this.loading = false;
      this.cdr.detectChanges();
      return;
    }

    this.api.get<any>('/bookings').pipe(
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.bookings = Array.isArray(raw) ? raw : (raw.bookings || []);
        this.cdr.detectChanges();
      },
      error: () => {
        this.cdr.detectChanges();
      }
    });
  }
}
