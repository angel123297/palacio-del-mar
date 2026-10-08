import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking } from '../../models/types';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 3rem 1.5rem; max-width: 1200px; margin: 0 auto;">
      <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); margin-bottom: 2rem;">Panel Administrativo</h1>

      <div *ngIf="loading" style="text-align: center; color: var(--gold); padding: 3rem;">
        Cargando gestión de reservas…
      </div>

      <div *ngIf="!loading">
        <table style="width: 100%; border-collapse: collapse; background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 8px; overflow: hidden;">
          <thead>
            <tr style="background: rgba(212,175,55,0.1); color: var(--gold); text-align: left;">
              <th style="padding: 12px 16px;">Código</th>
              <th style="padding: 12px 16px;">Cliente</th>
              <th style="padding: 12px 16px;">Check-in / Out</th>
              <th style="padding: 12px 16px;">Total</th>
              <th style="padding: 12px 16px;">Estado</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let b of bookings" style="border-bottom: 1px solid rgba(255,255,255,0.05);">
              <td style="padding: 12px 16px; font-weight: 600;">{{ b.bookingCode || b.id || b._id }}</td>
              <td style="padding: 12px 16px;">{{ b.guestName || b.guestEmail || 'Cliente' }}</td>
              <td style="padding: 12px 16px; font-size: 0.9rem;">{{ b.checkIn }} → {{ b.checkOut }}</td>
              <td style="padding: 12px 16px; color: var(--gold); font-weight: 700;">$ {{ b.totalPrice | number:'1.0-0' }}</td>
              <td style="padding: 12px 16px;">
                <span style="padding: 4px 10px; border-radius: 12px; font-size: 0.75rem; text-transform: uppercase; font-weight: 600; background: rgba(212,175,55,0.2); color: var(--gold);">
                  {{ b.status }}
                </span>
              </td>
            </tr>
            <tr *ngIf="bookings.length === 0">
              <td colspan="5" style="padding: 2rem; text-align: center; color: #888;">No hay reservas registradas en el sistema.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  bookings: Booking[] = [];
  loading = true;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/admin/bookings').subscribe({
      next: (res) => {
        const raw = res.data || res;
        this.bookings = Array.isArray(raw) ? raw : (raw.bookings || []);
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }
}
