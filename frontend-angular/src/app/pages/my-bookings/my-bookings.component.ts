import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { CurrencyService } from '../../services/currency.service';
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

      <div *ngIf="!loading && bookings.length === 0" style="text-align: center; padding: 3.5rem 1.5rem; background: rgba(255,255,255,0.02); border-radius: 16px; border: 1px solid rgba(212,175,55,0.25);">
        <div style="font-size: 2.5rem; margin-bottom: 0.8rem;">✨</div>
        <h2 style="font-family: var(--font-title); font-size: 1.6rem; color: var(--gold); margin-bottom: 0.5rem;">Tu próxima experiencia inolvidable te espera</h2>
        <p style="color: #ccc; font-size: 1rem; max-width: 500px; margin: 0 auto 2rem; line-height: 1.5;">Aún no tienes itinerarios activos en tu cuenta. Explora nuestras suites y casonas coloniales con vistas panorámicas al mar de Cartagena.</p>
        
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; max-width: 720px; margin: 0 auto 2.5rem; text-align: left;">
          <a [routerLink]="['/habitaciones/penthouse-bocagrande']" style="text-decoration: none; display: block; background: rgba(0,0,0,0.5); border: 1px solid rgba(201,169,110,0.2); border-radius: 10px; padding: 1.2rem; transition: transform 0.2s, border-color 0.2s; cursor: pointer;">
            <p style="color: var(--gold); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.15em; font-weight: 700; margin: 0 0 4px;">Frente al Mar</p>
            <h4 style="color: #fff; margin: 0 0 6px; font-size: 1rem;">Palacio Bocagrande &rarr;</h4>
            <p style="color: #888; font-size: 0.8rem; margin: 0;">Piscina infinita y atardeceres dorados sobre el mar.</p>
          </a>
          <a [routerLink]="['/habitaciones/suite-arte-patio-getsemani']" style="text-decoration: none; display: block; background: rgba(0,0,0,0.5); border: 1px solid rgba(201,169,110,0.2); border-radius: 10px; padding: 1.2rem; transition: transform 0.2s, border-color 0.2s; cursor: pointer;">
            <p style="color: var(--gold); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.15em; font-weight: 700; margin: 0 0 4px;">Patrimonio</p>
            <h4 style="color: #fff; margin: 0 0 6px; font-size: 1rem;">Casona Getsemaní &rarr;</h4>
            <p style="color: #888; font-size: 0.8rem; margin: 0;">Arquitectura histórica, patios coloniales y exclusividad.</p>
          </a>
        </div>

        <a routerLink="/" class="btn-primary" style="display: inline-flex; align-items: center; justify-content: center; min-height: 44px; padding: 12px 28px; font-weight: 700; text-decoration: none; font-size: 0.95rem;">
          Explorar Suites y Villas Exclusivas &rarr;
        </a>
      </div>

      <div *ngIf="!loading && bookings.length > 0" style="display: flex; flex-direction: column; gap: 1.5rem;">

        <div *ngFor="let booking of bookings" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 1.5rem; display: flex; flex-wrap: wrap; gap: 1.5rem; justify-content: space-between; align-items: center;">
          <div>
            <h3 style="margin: 0 0 6px 0; color: #ffffff;">{{ booking.suite?.name || 'Suite Palacio del Mar' }}</h3>
            <p style="color: #aaa; font-size: 0.85rem; margin: 0 0 10px 0;">Código: {{ booking.bookingCode || booking.id || booking._id }}</p>
            <div style="display: flex; gap: 1.5rem; font-size: 0.95rem; color: #ddd;">
              <span><strong>Llegada:</strong> {{ booking.checkIn | date:'dd/MM/yyyy' }}</span>
              <span><strong>Salida:</strong> {{ booking.checkOut | date:'dd/MM/yyyy' }}</span>
            </div>
          </div>

          <div style="text-align: right;">
            <div style="font-size: 1.4rem; font-weight: 700; color: var(--gold); margin-bottom: 6px;">{{ currency.formatPrice(booking.totalPrice || 0) }}</div>
            <div style="display: flex; gap: 10px; align-items: center; justify-content: flex-end;">
              <span style="display: inline-block; padding: 4px 12px; border-radius: 20px; font-size: 0.8rem; font-weight: 600; text-transform: uppercase;"
                    [style.background]="booking.status === 'confirmed' ? 'rgba(46, 204, 113, 0.2)' : 'rgba(241, 196, 15, 0.2)'"
                    [style.color]="booking.status === 'confirmed' ? '#2ecc71' : '#f1c40f'">
                {{ getStatusLabel(booking.status) }}
              </span>
              <a [routerLink]="['/reservas', booking.id || booking._id]" class="btn-outline" style="padding: 6px 16px; font-size: 0.85rem; text-decoration: none;">Ver Detalles</a>
              <a *ngIf="(booking.status === 'pending' || ((booking.balanceDue || 0) > 0 && booking.status !== 'cancelled' && booking.status !== 'expired' && booking.status !== 'checked_out')) && !isExpired(booking)" [routerLink]="['/checkout', booking.id || booking._id]" class="btn-primary" style="padding: 6px 16px; font-size: 0.85rem; text-decoration: none;">
                {{ booking.status === 'pending' ? 'Pagar' : 'Pagar Saldo' }}
              </a>
              <a *ngIf="booking.status === 'confirmed'" [routerLink]="['/reservas', booking.id || booking._id, 'comprobante']" class="btn-outline" style="padding: 6px 16px; font-size: 0.85rem; border-color: #2ecc71; color: #2ecc71; text-decoration: none;">Ver Factura</a>
              <button *ngIf="(booking.status === 'confirmed' || booking.status === 'pending') && !isExpired(booking)" (click)="openCancelModal(booking)" class="btn-outline" style="padding: 6px 16px; font-size: 0.85rem; border-color: #e74c3c; color: #e74c3c; background: transparent; cursor: pointer;">Cancelar</button>
            </div>
          </div>
        </div>

        <!-- Cancellation Policy Modal -->
        <div *ngIf="selectedCancelBooking" (keydown.escape)="closeCancelModal()" tabindex="-1" role="dialog" aria-modal="true" aria-label="Confirmar cancelación de reserva" style="position: fixed; inset: 0; background: rgba(0,0,0,0.85); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 1rem;">
          <div style="background: #111; border: 1px solid #c9a96e; border-radius: 12px; max-width: 500px; width: 100%; padding: 2rem; color: #f5f0e8;">
            <h3 style="font-family: var(--font-title); font-size: 1.5rem; color: #c9a96e; margin-top: 0; margin-bottom: 1rem;">Confirmar Cancelación</h3>
            <p style="font-size: 0.95rem; line-height: 1.5; color: #ccc; margin-bottom: 1.2rem;">
              Estás a punto de cancelar tu reserva en <strong>{{ selectedCancelBooking.suite?.name || 'Palacio del Mar' }}</strong> (Código: {{ selectedCancelBooking.bookingCode }}).
            </p>
            <div style="background: rgba(201,169,110,0.08); border: 1px solid rgba(201,169,110,0.3); border-radius: 8px; padding: 1rem; margin-bottom: 1.5rem; font-size: 0.9rem;">
              <p style="margin: 0 0 6px 0; color: #c9a96e; font-weight: 600;">Política de Devolución Aplicable:</p>
              <p style="margin: 0 0 8px 0; color: #ddd;">{{ cancelRefundInfo }}</p>
              <div style="display: flex; justify-content: space-between; font-size: 1rem; font-weight: 700; color: #fff; border-top: 1px solid rgba(255,255,255,0.1); padding-top: 8px;">
                <span>Monto a Reembolsar:</span>
                <span style="color: #2ecc71;">{{ currency.formatPrice(cancelRefundAmount) }}</span>
              </div>
            </div>
            <p *ngIf="cancelError" style="color: #ff4d4d; font-size: 0.85rem; margin-bottom: 1rem; background: rgba(231,76,60,0.1); padding: 8px; border-radius: 6px;">{{ cancelError }}</p>
            <div style="display: flex; gap: 1rem; justify-content: flex-end;">
              <button (click)="closeCancelModal()" [disabled]="cancelling" style="padding: 10px 18px; background: transparent; border: 1px solid rgba(255,255,255,0.2); color: #ccc; border-radius: 6px; cursor: pointer;">Volver</button>
              <button (click)="confirmCancellation()" [disabled]="cancelling" style="padding: 10px 18px; background: #e74c3c; border: none; color: #fff; border-radius: 6px; font-weight: 600; cursor: pointer;">
                {{ cancelling ? 'Cancelando...' : 'Confirmar Cancelación' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class MyBookingsComponent implements OnInit {
  bookings: Booking[] = [];
  loading = true;

  selectedCancelBooking: Booking | null = null;
  cancelling = false;
  cancelRefundInfo = '';
  cancelRefundAmount = 0;
  cancelError = '';

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef,
    private router: Router,
    public currency: CurrencyService
  ) {}

  /** L8: una reserva pending con TTL vencido ya no debe ofrecer pago ni cancelación. */
  isExpired(b: Booking): boolean {
    if (b.status !== 'pending' || !b.expiresAt) return false;
    return new Date(b.expiresAt).getTime() < Date.now();
  }

  getStatusLabel(status?: string): string {
    switch (status?.toLowerCase()) {
      case 'confirmed': return 'Confirmada';
      case 'pending': return 'Pendiente de pago';
      case 'cancelled': return 'Cancelada';
      case 'completed': return 'Completada';
      case 'checked_in': return 'En estadía';
      case 'checked_out': return 'Finalizada';
      case 'expired': return 'Expirada';
      default: return status || 'Pendiente';
    }
  }

  openCancelModal(b: Booking): void {
    this.selectedCancelBooking = b;
    const netPaid = Math.max(0, (b.paidAmount || 0) - (b.refundedAmount || 0));
    if (netPaid <= 0) {
      this.cancelRefundAmount = 0;
      this.cancelRefundInfo = 'No hay pagos netos registrados para reembolsar; la reserva pendiente se cancelará sin cargo.';
      return;
    }

    const diffDays = this.getDaysUntilCheckInBogota(b.checkIn);
    const totalPrice = b.totalPrice || 0;
    const deposit = b.guaranteeDeposit || 0;
    const retentionBase = Math.max(0, totalPrice - deposit);

    if (diffDays < 2) {
      this.cancelRefundAmount = Math.max(0, Math.min(netPaid, netPaid - retentionBase));
      this.cancelRefundInfo = deposit > 0 && this.cancelRefundAmount > 0
        ? 'Cancelación con menos de 48 horas: penalidad del 100% sobre el valor sin depósito; se reembolsará únicamente el depósito de garantía.'
        : 'Cancelación con menos de 48 horas (No-Show estricto): aplica penalidad del 100% sobre el valor sin depósito de garantía.';
    } else if (diffDays < 7) {
      const retention = Math.round(retentionBase * 0.10);
      this.cancelRefundAmount = Math.max(0, Math.min(netPaid, netPaid - retention));
      this.cancelRefundInfo = 'Cancelación con menos de 7 días: aplica retención del 10% sobre el total sin depósito de garantía. El monto exacto lo confirma el sistema al procesar.';
    } else {
      this.cancelRefundAmount = netPaid;
      this.cancelRefundInfo = 'Reembolso total del 100% de lo abonado neto sin retención por anticipación de 7 días o más.';
    }
  }

  private getDaysUntilCheckInBogota(checkIn?: string): number {
    if (!checkIn) return 999;
    const checkInStr = String(checkIn).slice(0, 10);
    const todayBogota = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
    const [cy, cm, cd] = checkInStr.split('-').map(Number);
    const [ty, tm, td] = todayBogota.split('-').map(Number);
    if (!cy || !cm || !cd || !ty || !tm || !td) {
      return Math.floor((new Date(checkIn).getTime() - Date.now()) / (1000 * 60 * 60 * 24));
    }
    const utcCheckIn = Date.UTC(cy, cm - 1, cd);
    const utcToday = Date.UTC(ty, tm - 1, td);
    return Math.round((utcCheckIn - utcToday) / (1000 * 60 * 60 * 24));
  }

  closeCancelModal(): void {
    this.selectedCancelBooking = null;
    this.cancelling = false;
    this.cancelError = '';
  }

  confirmCancellation(): void {
    if (!this.selectedCancelBooking) return;
    this.cancelling = true;
    this.cancelError = '';
    const id = this.selectedCancelBooking.id || this.selectedCancelBooking._id;

    this.api.post<any>(`/bookings/${id}/cancel`, { reason: 'Cancelación voluntaria del huésped' }).subscribe({
      next: () => {
        this.cancelling = false;
        this.selectedCancelBooking = null;
        this.fetchMyBookings();
      },
      error: (err) => {
        this.cancelling = false;
        this.cancelError = err?.error?.message || 'Error al cancelar la reserva';
        this.cdr.detectChanges();
      }
    });
  }

  fetchMyBookings(isBackgroundRefresh = false): void {
    if (!isBackgroundRefresh) {
      this.loading = true;
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
        if (!isBackgroundRefresh && this.bookings.some(b => b.status === 'pending' && !this.isExpired(b))) {
          setTimeout(() => this.fetchMyBookings(true), 2500);
        }
      },
      error: () => {
        this.cdr.detectChanges();
      }
    });
  }

  ngOnInit(): void {
    if (!this.auth.isLoggedIn()) {
      this.loading = false;
      this.cdr.detectChanges();
      return;
    }
    const role = (this.auth.currentUser()?.role || '').toLowerCase();
    if (role === 'host' || role === 'anfitrion') {
      this.router.navigate(['/anfitrion']);
      return;
    }
    if (role === 'admin') {
      this.router.navigate(['/admin']);
      return;
    }
    this.fetchMyBookings();
  }
}
