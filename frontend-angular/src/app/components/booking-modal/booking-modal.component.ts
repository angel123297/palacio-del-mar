import { Component, Input, Output, EventEmitter, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Suite } from '../../models/types';

@Component({
  selector: 'app-booking-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="isOpen" (click)="close()" style="position: fixed; inset: 0; background: rgba(0,0,0,0.85); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 1rem;">
      <div class="modal-content" (click)="$event.stopPropagation()" style="background: var(--dark); border: 1px solid var(--gold); border-radius: 12px; max-width: 550px; width: 100%; padding: 2rem; color: var(--cream); position: relative; max-height: 90vh; overflow-y: auto;">
        
        <button (click)="close()" style="position: absolute; top: 16px; right: 16px; background: none; border: none; color: #aaaaaa; font-size: 1.5rem; cursor: pointer;">×</button>

        <div *ngIf="suite">
          <span style="color: var(--gold); font-size: 0.8rem; letter-spacing: 2px; text-transform: uppercase; font-weight: 600;">Reserva Directa</span>
          <h2 style="margin: 4px 0 1rem 0; font-family: 'Cormorant Garamond', serif; font-size: 1.8rem; color: #ffffff;">{{ suite.name }}</h2>

          <div style="display: flex; gap: 8px; margin-bottom: 1.5rem;">
            <span class="chip" style="background: rgba(212,175,55,0.1); color: var(--gold); padding: 4px 10px; border-radius: 4px; font-size: 0.8rem;">
              {{ suite.type }}
            </span>
            <span class="chip" style="background: rgba(255,255,255,0.05); color: #ccc; padding: 4px 10px; border-radius: 4px; font-size: 0.8rem;">
              Hasta {{ suite.maxGuests || 2 }} huéspedes
            </span>
          </div>

          <form (ngSubmit)="confirmBooking()" style="display: flex; flex-direction: column; gap: 1rem;">
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
              <div>
                <label class="field-label">Fecha de Llegada</label>
                <input type="date" [(ngModel)]="checkIn" name="checkIn" required (change)="recalculateNights()" style="width: 100%;" />
              </div>
              <div>
                <label class="field-label">Fecha de Salida</label>
                <input type="date" [(ngModel)]="checkOut" name="checkOut" required (change)="recalculateNights()" style="width: 100%;" />
              </div>
            </div>

            <div>
              <label class="field-label">Huéspedes</label>
              <select [(ngModel)]="guests" name="guests" style="width: 100%;">
                <option [ngValue]="1">1 Huésped</option>
                <option [ngValue]="2">2 Huéspedes</option>
                <option [ngValue]="3">3 Huéspedes</option>
                <option [ngValue]="4">4 Huéspedes</option>
              </select>
            </div>

            <div>
              <label class="field-label">Nombre Completo</label>
              <input type="text" [(ngModel)]="guestName" name="guestName" required placeholder="Ej: Alejandro Silva" style="width: 100%;" />
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
              <div>
                <label class="field-label">Correo Electrónico</label>
                <input type="email" [(ngModel)]="guestEmail" name="guestEmail" required placeholder="ejemplo@correo.com" style="width: 100%;" />
              </div>
              <div>
                <label class="field-label">Teléfono</label>
                <input type="tel" [(ngModel)]="guestPhone" name="guestPhone" placeholder="+57 300..." style="width: 100%;" />
              </div>
            </div>

            <!-- Price Breakdown -->
            <div style="background: rgba(255,255,255,0.03); border: 1px solid rgba(212,175,55,0.2); border-radius: 8px; padding: 1rem; margin-top: 0.5rem;">
              <div style="display: flex; justify-content: space-between; font-size: 0.9rem; margin-bottom: 6px;">
                <span>Tarifa por noche:</span>
                <span>$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</span>
              </div>
              <div style="display: flex; justify-content: space-between; font-size: 0.9rem; margin-bottom: 6px;">
                <span>Noches de estancia:</span>
                <span>{{ nights }} noche(s)</span>
              </div>
              <div style="display: flex; justify-content: space-between; font-size: 1.1rem; font-weight: 700; color: var(--gold); border-top: 1px solid rgba(255,255,255,0.1); padding-top: 8px; margin-top: 8px;">
                <span>Total Estimado:</span>
                <span>$ {{ totalPrice | number:'1.0-0' }}</span>
              </div>
            </div>

            <button type="submit" class="btn-primary" [disabled]="submitting" style="width: 100%; padding: 12px; margin-top: 8px; font-size: 1rem;">
              {{ submitting ? 'Procesando reserva…' : 'Confirmar Reserva' }}
            </button>

            <p *ngIf="errorMessage" style="color: #ff4d4d; font-size: 0.85rem; text-align: center; margin: 0;">{{ errorMessage }}</p>
          </form>
        </div>

      </div>
    </div>
  `
})
export class BookingModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() suite: Suite | null = null;
  @Output() closeEvent = new EventEmitter<void>();

  checkIn: string = '';
  checkOut: string = '';
  guests: number = 2;
  guestName: string = '';
  guestEmail: string = '';
  guestPhone: string = '';

  nights: number = 1;
  totalPrice: number = 0;
  submitting = false;
  errorMessage = '';

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private router: Router
  ) {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 2);
    this.checkIn = today.toISOString().split('T')[0];
    this.checkOut = tomorrow.toISOString().split('T')[0];
  }

  ngOnChanges(): void {
    if (this.suite) {
      this.recalculateNights();
      const currentUser = this.auth.currentUserValue;
      if (currentUser) {
        this.guestName = currentUser.name || '';
        this.guestEmail = currentUser.email || '';
      }
    }
  }

  recalculateNights(): void {
    if (!this.checkIn || !this.checkOut) return;
    const d1 = new Date(this.checkIn);
    const d2 = new Date(this.checkOut);
    const diffTime = Math.abs(d2.getTime() - d1.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    this.nights = diffDays > 0 ? diffDays : 1;

    const base = this.suite?.basePrice || this.suite?.pricePerNight || 450000;
    this.totalPrice = base * this.nights;
  }

  close(): void {
    this.isOpen = false;
    this.closeEvent.emit();
  }

  confirmBooking(): void {
    if (!this.suite) return;
    this.submitting = true;
    this.errorMessage = '';

    const payload = {
      suiteId: this.suite.id || this.suite._id,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      guests: this.guests,
      guestName: this.guestName,
      guestEmail: this.guestEmail,
      guestPhone: this.guestPhone
    };

    this.api.post<any>('/bookings', payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.close();
        this.router.navigate(['/mis-reservas']);
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err.error?.message || 'No se pudo crear la reserva.';
      }
    });
  }
}
