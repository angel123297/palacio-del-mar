import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking } from '../../models/types';

@Component({
  selector: 'app-booking-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, CurrencyPipe],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 3rem 1.5rem;">
      <div style="max-width: 900px; margin: 0 auto;">
        
        <div *ngIf="loading" class="luxury-loader-container">
          <div class="luxury-spinner"></div>
          <p class="luxury-loader-text">Cargando detalles de tu reserva...</p>
        </div>

        <div *ngIf="!loading && !booking" style="text-align: center; color: #ff4d4d; padding: 4rem;">
          <h3>Reserva no encontrada</h3>
          <a routerLink="/mis-reservas" style="color: var(--gold); text-decoration: none; margin-top: 1rem; display: inline-block;">← Volver a Mis Reservas</a>
        </div>

        <div *ngIf="!loading && booking" class="bd-card">
          <div class="bd-header">
            <div>
              <a routerLink="/mis-reservas" class="bd-back">← Mis Reservas</a>
              <h1 style="font-family: var(--font-title); font-size: 2.2rem; margin: 1rem 0 0.5rem 0;">Detalles de la Reserva</h1>
              <p style="color: var(--gold); font-size: 1.1rem; letter-spacing: 1px;">Código: {{ booking.bookingCode || booking._id?.substring(0,8) | uppercase }}</p>
            </div>
            <div>
              <span class="status-badge" [ngClass]="booking.status">{{ booking.status | uppercase }}</span>
            </div>
          </div>

          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 2rem; margin-top: 2rem;">
            <!-- Info principal -->
            <div class="bd-section">
              <h3 class="bd-section-title">Estancia</h3>
              <p><strong>Suite:</strong> {{ suiteName }}</p>
              <p><strong>Check-in:</strong> {{ booking.checkIn | date:'longDate' }}</p>
              <p><strong>Check-out:</strong> {{ booking.checkOut | date:'longDate' }}</p>
              <p><strong>Huéspedes:</strong> {{ booking.guests }}</p>
            </div>

            <!-- Servicios Adicionales -->
            <div class="bd-section" *ngIf="hasExtraServices()">
              <h3 class="bd-section-title">Servicios Adicionales</h3>
              <ul style="list-style: none; padding: 0; margin: 0; color: #ccc; font-size: 0.95rem; display: flex; flex-direction: column; gap: 0.8rem;">
                <li *ngIf="booking.airportPickup" style="display: flex; gap: 0.5rem;"><span style="color: var(--gold);">🚗</span> <span>Recogida VIP en aeropuerto</span></li>
                <li *ngFor="let exp of booking.experiences" style="display: flex; gap: 0.5rem;"><span style="color: var(--gold);">✨</span> <span>{{ exp.name }} (x{{ exp.quantity }})</span></li>
                <li *ngFor="let food of booking.foodOrders" style="display: flex; gap: 0.5rem;"><span style="color: var(--gold);">🍽️</span> <span>Desayuno Flotante / Cena (x1)</span></li>
              </ul>
            </div>

            <!-- Detalles del pago -->
            <div class="bd-section">
              <h3 class="bd-section-title">Resumen de Pago</h3>
              <p><strong>Precio Total:</strong>&nbsp;<span style="color: var(--gold); font-weight: bold; font-size: 1.2rem;">$ {{ booking.totalPrice | number:'1.0-0' }}</span></p>
              
              <div *ngIf="booking.status === 'pending'" style="margin-top: 1.5rem;">
                <p style="color: #ff9f43; margin-bottom: 1rem; font-size: 0.9rem;">⚠️ Tu reserva está pendiente de pago. Para confirmar tu estancia, realiza el pago ahora.</p>
                <a [routerLink]="['/checkout', booking._id || booking.id]" class="btn-primary" style="display: block; text-align: center; padding: 12px; text-decoration: none;">Ir al Centro de Pagos</a>
              </div>
              
              <div *ngIf="booking.status === 'confirmed' || booking.status === 'completed' || booking.status === 'paid'" style="margin-top: 1.5rem;">
                <p style="color: #2ecc71; margin-bottom: 1rem; font-size: 0.9rem;">✅ Reserva pagada y confirmada.</p>
                <a [routerLink]="['/reservas', booking._id || booking.id, 'comprobante']" class="btn-outline" style="display: block; text-align: center; padding: 12px; text-decoration: none;">Ver Factura / Comprobante</a>
              </div>
            </div>
          </div>

          <div class="bd-section" style="margin-top: 2rem;" *ngIf="booking.notes">
            <h3 class="bd-section-title">Peticiones Especiales / Notas</h3>
            <p style="color: #ddd; font-style: italic; background: rgba(0,0,0,0.3); padding: 1rem; border-left: 3px solid var(--gold);">{{ booking.notes }}</p>
          </div>

          <div class="bd-section" style="margin-top: 2rem;">
            <h3 class="bd-section-title">Ubicación y Llegada</h3>
            <div *ngIf="booking.airportPickup" style="background: rgba(46,204,113,0.1); padding: 1rem; border-radius: 8px; border: 1px solid rgba(46,204,113,0.3); margin-bottom: 1.5rem;">
              <p style="color: #2ecc71; margin: 0; font-weight: bold; display: flex; align-items: center; gap: 0.5rem;"><span style="font-size: 1.2rem;">🚗</span> Tienes recogida VIP en el aeropuerto incluida.</p>
              <p style="color: #ccc; margin: 0.5rem 0 0 0; font-size: 0.95rem; line-height: 1.5;">Nuestro conductor estará esperándote en la puerta de llegadas con un cartel a tu nombre. Tu vuelo ha sido registrado.</p>
            </div>
            <div *ngIf="!booking.airportPickup" style="background: rgba(255,255,255,0.05); padding: 1rem; border-radius: 8px; border: 1px solid rgba(255,255,255,0.1); margin-bottom: 1.5rem;">
              <p style="color: #fff; margin: 0; font-weight: bold; display: flex; align-items: center; gap: 0.5rem;"><span style="font-size: 1.2rem;">🚕</span> ¿Cómo llegar al hotel?</p>
              <p style="color: #ccc; margin: 0.5rem 0 1rem 0; font-size: 0.95rem; line-height: 1.5;">Tu reserva no incluye traslado. Te recomendamos tomar un taxi oficial desde el aeropuerto o seguir las indicaciones de navegación.</p>
              <a [href]="getMapsLink()" target="_blank" style="color: #000; background: var(--gold); padding: 8px 16px; border-radius: 6px; text-decoration: none; font-weight: bold; display: inline-flex; align-items: center; gap: 0.5rem; transition: background 0.3s;">
                <span>📍</span> Abrir en Google Maps
              </a>
            </div>
            
            <div style="border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; overflow: hidden; height: 300px;">
              <iframe src="https://www.openstreetmap.org/export/embed.html?bbox=-75.56846141815187%2C10.420601934399738%2C-75.53816318511964%2C10.434417758994511&amp;layer=mapnik" width="100%" height="100%" style="border:0;" allowfullscreen="" loading="lazy"></iframe>
            </div>
          </div>

        </div>
      </div>
    </div>
  `,
  styles: [`
    .bd-card { background: rgba(255,255,255,0.02); border: 1px solid rgba(212,175,55,0.2); border-radius: 12px; padding: 2.5rem; }
    .bd-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 1.5rem; }
    .bd-back { color: #aaa; text-decoration: none; font-size: 0.9rem; transition: color 0.3s; }
    .bd-back:hover { color: var(--gold); }
    .bd-section { background: rgba(0,0,0,0.4); border: 1px solid rgba(212,175,55,0.1); border-radius: 8px; padding: 1.5rem; }
    .bd-section-title { color: var(--gold); margin-bottom: 1rem; font-size: 1.2rem; font-family: var(--font-title); border-bottom: 1px solid rgba(212,175,55,0.1); padding-bottom: 0.5rem; }
    .bd-section p { margin: 0.5rem 0; color: #ccc; }
    
    .status-badge { padding: 6px 14px; border-radius: 20px; font-size: 0.8rem; font-weight: bold; letter-spacing: 1px; }
    .status-badge.pending { background: rgba(255,159,67,0.15); color: #ff9f43; border: 1px solid rgba(255,159,67,0.3); }
    .status-badge.confirmed { background: rgba(46,204,113,0.15); color: #2ecc71; border: 1px solid rgba(46,204,113,0.3); }
    .status-badge.completed { background: rgba(52,152,219,0.15); color: #3498db; border: 1px solid rgba(52,152,219,0.3); }
    .status-badge.cancelled { background: rgba(231,76,60,0.15); color: #e74c3c; border: 1px solid rgba(231,76,60,0.3); }
  `]
})
export class BookingDetailComponent implements OnInit {
  booking: any = null;
  loading = true;

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.api.get<any>(`/bookings/${id}`).subscribe({
        next: (res) => {
          this.booking = res.data || res;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        }
      });
    } else {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  get suiteName(): string {
    if (!this.booking?.suite) return 'Suite Desconocida';
    return typeof this.booking.suite === 'string' ? this.booking.suite : this.booking.suite.name;
  }

  hasExtraServices(): boolean {
    if (!this.booking) return false;
    const hasExperiences = this.booking.experiences && this.booking.experiences.length > 0;
    const hasFood = this.booking.foodOrders && this.booking.foodOrders.length > 0;
    return !!(this.booking.airportPickup || hasExperiences || hasFood);
  }

  getMapsLink(): string {
    if (!this.booking) return '#';
    let query = 'Cartagena';
    if (this.booking.branch && this.booking.branch.name) {
      query = this.booking.branch.name;
    } else if (this.booking.suite && this.booking.suite.branch && this.booking.suite.branch.name) {
      query = this.booking.suite.branch.name;
    }
    return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`;
  }
}
