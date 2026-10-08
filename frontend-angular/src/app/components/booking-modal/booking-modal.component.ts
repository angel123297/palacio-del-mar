import { Component, Input, Output, EventEmitter, OnChanges, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Suite } from '../../models/types';
import { finalize } from 'rxjs';

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
              <select [(ngModel)]="guests" name="guests" style="width: 100%; background-color: #141414; color: #f5e6c8; border: 1px solid rgba(212,175,55,0.3); border-radius: 6px; padding: 10px; font-size: 0.95rem;">
                <option *ngFor="let opt of guestOptions" [ngValue]="opt" style="background-color: #141414; color: #f5e6c8;">
                  {{ opt }} {{ opt === 1 ? 'Huésped' : 'Huéspedes' }}
                </option>
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

            <p *ngIf="successMessage" style="color: #2ecc71; font-size: 0.95rem; text-align: center; margin-top: 8px; background: rgba(46,204,113,0.1); padding: 10px; border-radius: 6px;">
              {{ successMessage }}
            </p>

            <p *ngIf="errorMessage" style="color: #ff4d4d; font-size: 0.9rem; text-align: center; margin-top: 8px; background: rgba(255,77,77,0.1); padding: 8px; border-radius: 6px;">
              {{ errorMessage }}
            </p>
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
  successMessage = '';

  constructor(
    private api: ApiService,
    private auth: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 2);
    this.checkIn = today.toISOString().split('T')[0];
    this.checkOut = tomorrow.toISOString().split('T')[0];
  }

  get maxGuestsCount(): number {
    return Math.max(1, this.suite?.maxGuests || (this.suite as any)?.capacity || 2);
  }

  get guestOptions(): number[] {
    const max = this.maxGuestsCount;
    const options: number[] = [];
    for (let i = 1; i <= max; i++) {
      options.push(i);
    }
    return options;
  }

  ngOnChanges(): void {
    if (this.suite) {
      if (this.guests > this.maxGuestsCount) {
        this.guests = this.maxGuestsCount;
      }
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
    this.errorMessage = '';
    this.successMessage = '';
    this.closeEvent.emit();
    this.cdr.detectChanges();
  }

  private validate(): string {
    const name = (this.guestName || '').trim();
    const email = (this.guestEmail || '').trim();
    const missing: string[] = [];
    if (!this.checkIn) missing.push('fecha de llegada');
    if (!this.checkOut) missing.push('fecha de salida');
    if (!name) missing.push('nombre completo');
    if (!email) missing.push('correo electrónico');
    if (missing.length) return `Por favor completa: ${missing.join(', ')}.`;

    const today = new Date().toISOString().split('T')[0];
    if (this.checkIn < today) return 'La fecha de llegada no puede ser en el pasado.';
    if (this.checkOut <= this.checkIn) return 'La fecha de salida debe ser posterior a la de llegada.';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return 'Ingresa un correo electrónico válido.';
    if (name.length < 3) return 'Ingresa tu nombre completo.';
    if (this.guests < 1 || this.guests > this.maxGuestsCount) return `Esta habitación admite máximo ${this.maxGuestsCount} huéspedes.`;
    return '';
  }

  private friendlyError(err: any): string {
    const msg: string = err?.error?.message || '';
    if (err?.status === 0) return 'No hay conexión con el servidor. Intenta de nuevo en unos segundos.';
    if (msg.startsWith('Suite no encontrada')) return 'Esta habitación ya no existe o no está disponible. Recarga la página y elige otra.';
    return msg || 'No se pudo procesar la reserva. Intenta de nuevo.';
  }

  confirmBooking(): void {
    if (!this.suite || this.submitting) return;
    this.successMessage = '';
    this.errorMessage = this.validate();
    if (this.errorMessage) return;

    this.submitting = true;

    const payload = {
      suiteId: this.suite.id || this.suite._id,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      guests: this.guests,
      guestName: (this.guestName || '').trim(),
      guestEmail: (this.guestEmail || '').trim(),
      guestPhone: (this.guestPhone || '').trim()
    };

    this.api.post<any>('/bookings', payload).pipe(
      finalize(() => {
        this.submitting = false;
      })
    ).subscribe({
      next: (res) => {
        if (this.auth.getToken()) {
          this.close();
          this.router.navigate(['/mis-reservas']);
          return;
        }
        const code = res?.data?.bookingCode;
        this.successMessage = `¡Reserva creada!${code ? ' Código: ' + code + '.' : ''} Guarda este código para consultar tu reserva.`;
      },
      error: (err) => {
        this.errorMessage = this.friendlyError(err);
      }
    });
  }
}

