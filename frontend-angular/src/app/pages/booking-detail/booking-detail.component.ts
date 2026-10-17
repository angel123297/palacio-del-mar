import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking } from '../../models/types';

@Component({
  selector: 'app-booking-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
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

          <!-- Tarjeta de Llave Digital & Self Check-in si está confirmada o checked_in -->
          <div class="bd-section digital-key-box" style="margin-top: 1.5rem;" *ngIf="booking.status === 'confirmed' || booking.status === 'checked_in'">
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.8rem;">
              <h3 class="bd-section-title" style="margin: 0; border: none; padding: 0; display: flex; align-items: center; gap: 0.5rem;">
                <span>🔑</span> Llave Digital & Self Check-in
              </h3>
              <span class="status-badge" [ngClass]="booking.status === 'checked_in' ? 'checked_in' : 'confirmed'">
                {{ booking.status === 'checked_in' ? '🟢 CHECK-IN ACTIVO' : '⏰ PROGRAMADO 15:00 COP' }}
              </span>
            </div>
            <p style="font-size: 0.95rem; color: #ddd; margin: 0.4rem 0;">
              Tu estancia cuenta con <strong>ingreso 100% automatizado</strong> mediante cerradura inteligente.
            </p>

            <div class="pin-display">
              <div>
                <div style="font-size: 0.75rem; letter-spacing: 1.5px; color: #aaa; margin-bottom: 2px;">PIN DE ACCESO INTELIGENTE:</div>
                <div class="pin-code">{{ booking.smartLockPin || 'GENERANDO...' }}</div>
              </div>
              <button *ngIf="booking.smartLockPin" class="btn-copy" (click)="copyPin(booking.smartLockPin)">📋 Copiar PIN</button>
            </div>

            <div style="margin-top: 1rem; font-size: 0.85rem; color: #bbb; background: rgba(0,0,0,0.3); padding: 0.9rem; border-radius: 6px; display: flex; flex-direction: column; gap: 0.4rem;">
              <div>🕒 <strong>Check-in automático:</strong> 15:00 COP del día de llegada. El PIN se activará para la cerradura.</div>
              <div>🕒 <strong>Check-out automático:</strong> 12:00 COP del día de salida. El PIN se desactiva y se libera el depósito.</div>
              <div *ngIf="booking.guaranteeDeposit">
                🛡️ <strong>Depósito de Garantía:</strong> $ {{ booking.guaranteeDeposit | number:'1.0-0' }} COP
                <span *ngIf="booking.guaranteeRefunded" style="color: #2ecc71; font-weight: bold;">(Reembolsado automáticamente)</span>
                <span *ngIf="!booking.guaranteeRefunded" style="color: #f1c40f;">(Reembolso automático tras check-out a las 12:00)</span>
              </div>
            </div>
          </div>

          <!-- Tarjeta de Estancia Finalizada / Check-out procesado -->
          <div class="bd-section" style="margin-top: 1.5rem; border-color: rgba(46,204,113,0.3); background: rgba(46,204,113,0.05);" *ngIf="booking.status === 'checked_out' || booking.status === 'completed'">
            <h3 class="bd-section-title" style="margin: 0 0 0.5rem 0; border: none; padding: 0; color: #2ecc71; display: flex; align-items: center; gap: 0.5rem;">
              <span>🏁</span> Estancia Concluida
            </h3>
            <p style="color: #ddd; margin: 0.3rem 0; font-size: 0.95rem;">
              Check-out automático procesado exitosamente. La cerradura inteligente ha sido deshabilitada.
            </p>
            <p style="color: #2ecc71; margin: 0.3rem 0; font-size: 0.9rem;" *ngIf="booking.guaranteeRefunded">
              🛡️ Tu depósito de garantía de $ {{ (booking.guaranteeDeposit || 150000) | number:'1.0-0' }} COP fue liberado y reembolsado sin novedades.
            </p>
          </div>

          <!-- Sección de Reseña Verificada si ya hizo check-out -->
          <div class="bd-section" style="margin-top: 1.5rem;" *ngIf="booking.status === 'checked_out' || booking.status === 'completed'">
            <h3 class="bd-section-title" style="display: flex; align-items: center; gap: 0.5rem;">
              <span>⭐</span> Calificación Verificada de tu Estancia
            </h3>

            <div *ngIf="reviewSuccess" style="background: rgba(46,204,113,0.15); border: 1px solid #2ecc71; padding: 1rem; border-radius: 8px; color: #2ecc71;">
              <p style="margin: 0; font-weight: bold;">¡Muchas gracias por tu reseña verificada!</p>
              <p style="margin: 0.3rem 0 0; font-size: 0.85rem; color: #ddd;">
                Tu calificación fue registrada y ayuda al anfitrión a seguir brindando un servicio de alta calidad.
              </p>
            </div>

            <div *ngIf="!reviewSuccess">
              <p style="color: #ccc; font-size: 0.9rem; margin-bottom: 1rem;">
                Tu opinión es 100% verificada con tu estadía. Si reportas alguna novedad de mantenimiento, nuestro sistema notificará automáticamente al anfitrión.
              </p>

              <div style="margin-bottom: 1rem;">
                <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 0.4rem;">Calificación:</label>
                <div style="display: flex; gap: 0.6rem; font-size: 1.8rem; cursor: pointer; user-select: none;">
                  <span *ngFor="let star of [1,2,3,4,5]" 
                        (click)="reviewRating = star"
                        [style.color]="star <= reviewRating ? 'var(--gold)' : '#444'">
                    ★
                  </span>
                </div>
              </div>

              <div style="margin-bottom: 1rem;">
                <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 0.4rem;">Comentarios sobre tu experiencia:</label>
                <textarea [(ngModel)]="reviewComment" 
                          rows="3" 
                          class="luxury-input" 
                          placeholder="Cuéntanos qué tal estuvo la comodidad, limpieza, aire acondicionado, etc..."
                          style="width: 100%; resize: vertical; box-sizing: border-box;"></textarea>
              </div>

              <div *ngIf="reviewError" style="color: #ff6b6b; font-size: 0.85rem; margin-bottom: 0.8rem;">
                {{ reviewError }}
              </div>

              <button class="btn-primary" 
                      [disabled]="isSubmittingReview || !reviewRating" 
                      (click)="submitReview()" 
                      style="padding: 10px 22px; font-size: 0.9rem; cursor: pointer;">
                {{ isSubmittingReview ? 'Enviando...' : 'Publicar Reseña Verificada' }}
              </button>
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
              
              <div *ngIf="booking.status === 'confirmed' || booking.status === 'completed' || booking.status === 'paid' || booking.status === 'checked_in' || booking.status === 'checked_out'" style="margin-top: 1.5rem;">
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
    .status-badge.checked_in { background: rgba(0, 206, 201, 0.15); color: #00cec9; border: 1px solid rgba(0, 206, 201, 0.3); }
    .status-badge.checked_out { background: rgba(108, 92, 231, 0.15); color: #a29bfe; border: 1px solid rgba(108, 92, 231, 0.3); }
    .status-badge.completed { background: rgba(52,152,219,0.15); color: #3498db; border: 1px solid rgba(52,152,219,0.3); }
    .status-badge.cancelled { background: rgba(231,76,60,0.15); color: #e74c3c; border: 1px solid rgba(231,76,60,0.3); }

    .digital-key-box {
      border: 1px solid rgba(212,175,55,0.4);
      background: linear-gradient(135deg, rgba(212,175,55,0.08) 0%, rgba(0,0,0,0.6) 100%);
    }
    .pin-display {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #000;
      padding: 0.9rem 1.2rem;
      border-radius: 8px;
      border: 1px dashed var(--gold);
      margin-top: 0.8rem;
    }
    .pin-code {
      font-family: monospace;
      font-size: 1.8rem;
      letter-spacing: 5px;
      font-weight: bold;
      color: var(--gold);
    }
    .btn-copy {
      background: rgba(212,175,55,0.15);
      border: 1px solid var(--gold);
      color: var(--gold);
      padding: 6px 14px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.85rem;
      font-weight: bold;
      transition: all 0.2s;
    }
    .btn-copy:hover {
      background: var(--gold);
      color: #000;
    }
    .luxury-input {
      background: #151515;
      border: 1px solid rgba(255,255,255,0.15);
      color: #ffffff;
      padding: 10px;
      border-radius: 6px;
      font-family: inherit;
    }
    .luxury-input:focus {
      border-color: var(--gold);
      outline: none;
    }
  `]
})
export class BookingDetailComponent implements OnInit {
  booking: any = null;
  loading = true;

  // Reseña
  reviewRating = 5;
  reviewComment = '';
  isSubmittingReview = false;
  reviewSuccess = false;
  reviewError = '';

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

  copyPin(pin: string): void {
    if (pin && navigator.clipboard) {
      navigator.clipboard.writeText(pin).then(() => {
        alert('PIN de la cerradura copiado al portapapeles');
      });
    }
  }

  submitReview(): void {
    if (!this.booking) return;
    this.isSubmittingReview = true;
    this.reviewError = '';

    const payload = {
      bookingId: this.booking._id || this.booking.id,
      rating: this.reviewRating,
      comment: this.reviewComment
    };

    this.api.post<any>('/reviews/verified', payload).subscribe({
      next: () => {
        this.isSubmittingReview = false;
        this.reviewSuccess = true;
        this.cdr.detectChanges();
      },
      error: (err) => {
        this.isSubmittingReview = false;
        this.reviewError = err.error?.message || 'Error al enviar la reseña.';
        this.cdr.detectChanges();
      }
    });
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

