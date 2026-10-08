import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CustomDatepickerComponent } from '../../components/custom-datepicker/custom-datepicker.component';
import { CustomGuestpickerComponent } from '../../components/custom-guestpicker/custom-guestpicker.component';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Suite } from '../../models/types';

@Component({
  selector: 'app-suite-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, CustomDatepickerComponent, CustomGuestpickerComponent],
  template: `
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 2rem 1.5rem; max-width: 1100px; margin: 0 auto;">
      <div *ngIf="loading" class="luxury-loader-container">
        <div class="luxury-spinner"></div>
        <p class="luxury-loader-text">Cargando...</p>
      </div>

      <div *ngIf="!loading && !suite" style="text-align: center; color: #ff4d4d; padding: 4rem;">
        <h3>La suite que buscas no existe o no está disponible.</h3>
        <a routerLink="/" style="color: var(--gold); text-decoration: none; margin-top: 1rem; display: inline-block;">← Volver al inicio</a>
      </div>

      <div *ngIf="!loading && suite">
        <a routerLink="/" style="color: var(--gold); text-decoration: none; font-size: 0.9rem;">← Volver al inicio</a>
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 2.5rem; margin-top: 1.5rem;">
          <div>
            <img [src]="suite.mainImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800'" [alt]="suite.name" style="width: 100%; height: 380px; object-fit: cover; border-radius: 12px; border: 1px solid rgba(212, 175, 55, 0.2);">
            <h1 style="font-family: var(--font-title); font-size: 2rem; margin: 1.5rem 0 0.5rem 0;">{{ suite.name }}</h1>
            <p style="color: var(--gold); font-size: 0.95rem;">{{ suite.type }} · {{ suite.branch?.name || 'Centro Histórico' }}</p>
            <p style="color: #cccccc; line-height: 1.6; margin-top: 1rem;">{{ suite.description }}</p>
          </div>

          <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.25); border-radius: 12px; padding: 2rem;">
            <h3 style="color: var(--gold); margin: 0 0 1rem 0; font-size: 1.4rem;">Reservar esta Suite</h3>
            <div style="margin-bottom: 1.5rem;">
              <strong style="font-size: 1.8rem;">$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</strong>
              <span style="color: #aaa;"> / noche</span>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem;">
              <app-custom-datepicker label="Check-in" [date]="checkIn" (dateChange)="checkIn = $event"></app-custom-datepicker>
              <app-custom-datepicker label="Check-out" [date]="checkOut" (dateChange)="checkOut = $event"></app-custom-datepicker>
              <app-custom-guestpicker [adults]="guests" [maxGuests]="suite.maxGuests || suite.maxGuests || 2" (adultsChange)="guests = $event"></app-custom-guestpicker>
            </div>

            <button (click)="createBooking()" [disabled]="bookingBusy" style="width: 100%; background: var(--gold); color: #0d0d0d; font-weight: 700; padding: 14px; border: none; border-radius: 6px; font-size: 1rem; cursor: pointer;">
              {{ bookingBusy ? 'Procesando…' : 'Confirmar Reserva' }}
            </button>
            <p *ngIf="message" style="margin-top: 1rem; text-align: center; font-size: 0.9rem;" [style.color]="isError ? '#ff4d4d' : 'var(--gold)'">{{ message }}</p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class SuiteDetailComponent implements OnInit {
  suite: Suite | null = null;
  loading = true;
  bookingBusy = false;
  checkIn = new Date().toISOString().slice(0, 10);
  checkOut = new Date(Date.now() + 86400000 * 2).toISOString().slice(0, 10);
  guests = 2;

  get guestOptions(): number[] {
    const max = Math.max(1, this.suite?.maxGuests || (this.suite as any)?.capacity || 2);
    const opts = [];
    for (let i = 1; i <= max; i++) opts.push(i);
    return opts;
  }
  message = '';
  isError = false;

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private auth: AuthService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    const idOrSlug = this.route.snapshot.paramMap.get('idOrSlug');
    if (idOrSlug) {
      this.api.get<any>(`/suites/${idOrSlug}`).subscribe({
        next: (res) => {
          this.suite = res.data || res;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        }
      });
    }
  }

  createBooking(): void {
    if (!this.auth.getToken()) {
      this.message = 'Por favor inicia sesión para realizar tu reserva.';
      this.isError = true;
      return;
    }
    if (!this.suite) return;

    if (!this.checkIn || !this.checkOut) {
      this.message = 'Por favor selecciona las fechas de llegada y salida.';
      this.isError = true;
      return;
    }
    const today = new Date().toISOString().split('T')[0];
    if (this.checkIn < today) {
      this.message = 'La fecha de llegada no puede ser en el pasado.';
      this.isError = true;
      return;
    }
    if (this.checkOut <= this.checkIn) {
      this.message = 'La fecha de salida debe ser posterior a la de llegada.';
      this.isError = true;
      return;
    }


    this.bookingBusy = true;
    this.message = '';

    this.api.post<any>('/bookings', {
      suiteId: this.suite.id || this.suite._id,
      suite: this.suite.id || this.suite._id,
      checkIn: this.checkIn,
      checkOut: this.checkOut,
      guests: Number(this.guests)
    }).subscribe({
      next: () => {
        this.bookingBusy = false;
        this.message = '¡Reserva creada con éxito!';
        this.isError = false;
      },
      error: (err) => {
        this.bookingBusy = false;
        this.message = err.error?.message || 'No se pudo crear la reserva.';
        this.isError = true;
      }
    });
  }
}
