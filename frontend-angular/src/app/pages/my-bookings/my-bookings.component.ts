import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { LoyaltyService } from '../../services/loyalty.service';
import { Booking } from '../../models/types';
import { finalize } from 'rxjs';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 3rem 1.5rem; max-width: 1100px; margin: 0 auto;">
      <h1 style="font-family: var(--font-title); font-size: 2.2rem; color: var(--gold); margin-bottom: 2rem;">Mis Reservas</h1>

      <div *ngIf="loading" class="luxury-loader-container">
        <div class="luxury-spinner"></div>
        <p class="luxury-loader-text">Cargando...</p>
      </div>

      <div *ngIf="!loading && bookings.length === 0" style="text-align: center; padding: 4rem 1rem; background: rgba(255,255,255,0.02); border-radius: 12px; border: 1px solid rgba(212,175,55,0.2);">
        <p style="color: #aaa; font-size: 1.1rem; margin-bottom: 1.5rem;">No tienes reservas registradas en tu cuenta.</p>
        <a routerLink="/" style="background: var(--gold); color: #0d0d0d; font-weight: 700; padding: 12px 24px; border-radius: 6px; text-decoration: none;">Explorar Suites</a>
      </div>

      <div *ngIf="!loading && bookings.length > 0" style="display: flex; flex-direction: column; gap: 1.5rem;">
        
        <!-- Palace Club Summary -->
        <div style="background: linear-gradient(135deg, rgba(212,175,55,0.1) 0%, rgba(0,0,0,0.8) 100%); border: 1px solid rgba(212,175,55,0.3); border-radius: 12px; padding: 1.5rem; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 1rem;">
          <div>
            <h2 style="font-family: var(--font-title); font-size: 1.2rem; color: var(--gold); margin: 0 0 0.5rem 0; text-transform: uppercase;">The Palace Club</h2>
            <p style="color: #ccc; font-size: 0.9rem; margin: 0;">Nivel Actual: <strong style="color: #fff;">{{ loyalty.status().tier }}</strong></p>
          </div>
          <div style="flex-grow: 1; max-width: 400px;">
            <div style="display: flex; justify-content: space-between; font-size: 0.8rem; color: #aaa; margin-bottom: 4px;">
              <span>{{ loyalty.status().points | number }} pts</span>
              <span>Faltan {{ loyalty.status().nextTierPoints - loyalty.status().points | number }} pts para el siguiente nivel</span>
            </div>
            <div style="background: rgba(255,255,255,0.1); border-radius: 20px; height: 8px; width: 100%; overflow: hidden;">
              <div style="background: var(--gold); height: 100%; transition: width 1s ease-in-out;" [style.width.%]="loyalty.progressPercentage()"></div>
            </div>
          </div>
        </div>

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
            <div style="display: flex; gap: 10px; align-items: center; justify-content: flex-end;">
              <span style="display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; text-transform: uppercase;"
                    [style.background]="booking.status === 'confirmed' ? 'rgba(46, 204, 113, 0.2)' : 'rgba(241, 196, 15, 0.2)'"
                    [style.color]="booking.status === 'confirmed' ? '#2ecc71' : '#f1c40f'">
                {{ booking.status }}
              </span>
              <a [routerLink]="['/reservas', booking.id || booking._id]" class="btn-outline" style="padding: 6px 16px; font-size: 0.85rem; text-decoration: none;">Ver Detalles</a>
              <a *ngIf="booking.status === 'pending'" [routerLink]="['/checkout', booking.id || booking._id]" class="btn-primary" style="padding: 6px 16px; font-size: 0.85rem; text-decoration: none;">Pagar</a>
              <a *ngIf="booking.status === 'confirmed'" [routerLink]="['/reservas', booking.id || booking._id, 'comprobante']" class="btn-outline" style="padding: 6px 16px; font-size: 0.85rem; border-color: #2ecc71; color: #2ecc71; text-decoration: none;">Ver Factura</a>
            </div>
            <p *ngIf="payError && (payingId === booking.id || payingId === booking._id)" style="color: #ff4d4d; font-size: 0.8rem; margin-top: 5px;">{{ payError }}</p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class MyBookingsComponent implements OnInit {
  bookings: Booking[] = [];
  loading = true;
  payingId: string | null = null;
  payError: string = '';

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
    private router: Router,
    public loyalty: LoyaltyService
  ) {}

  printInvoice(booking: any) {
    const w = window.open('', '_blank');
    if (w) {
      w.document.write(`
        <html>
        <head>
          <title>Factura - ${booking.bookingCode || booking.id}</title>
          <style>
            body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; color: #333; padding: 40px; }
            .header { text-align: center; margin-bottom: 40px; }
            h1 { color: #d4af37; margin: 0; }
            .details { margin-bottom: 30px; }
            .details table { width: 100%; border-collapse: collapse; }
            .details th, .details td { text-align: left; padding: 12px; border-bottom: 1px solid #ddd; }
            .total { text-align: right; font-size: 1.5em; font-weight: bold; margin-top: 30px; color: #d4af37; }
            @media print {
              .no-print { display: none; }
            }
          </style>
        </head>
        <body>
          <div class="no-print" style="margin-bottom: 20px;">
            <button onclick="window.print()" style="padding: 10px 20px; background: #d4af37; color: white; border: none; cursor: pointer; font-size: 16px;">Imprimir PDF</button>
          </div>
          <div class="header">
            <h1>Palacio del Mar</h1>
            <p>Factura de Reserva</p>
          </div>
          <div class="details">
            <table>
              <tr><th>Reserva ID</th><td>${booking.bookingCode || booking.id || booking._id}</td></tr>
              <tr><th>Suite</th><td>${booking.suite?.name || 'Suite de Lujo'}</td></tr>
              <tr><th>Llegada</th><td>${new Date(booking.checkIn).toLocaleDateString()}</td></tr>
              <tr><th>Salida</th><td>${new Date(booking.checkOut).toLocaleDateString()}</td></tr>
              <tr><th>Estado</th><td>Pagado (Confirmada)</td></tr>
            </table>
          </div>
          <div class="total">
            Total Pagado: $ ${booking.totalPrice.toLocaleString('es-CO')}
          </div>
          <p style="margin-top: 50px; text-align: center; color: #888; font-size: 0.9em;">Gracias por su preferencia.</p>
        </body>
        </html>
      `);
      w.document.close();
    }
  }

  payBooking(booking: any): void {
    const id = booking.id || booking._id;
    this.router.navigate(['/checkout', id]);
  }

  ngOnInit(): void {
    if (!this.auth.isLoggedIn()) {
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
