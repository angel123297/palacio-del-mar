import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { Suite } from '../../models/types';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { FooterComponent } from '../../components/footer/footer.component';

@Component({
  selector: 'app-suite-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, NavbarComponent, FooterComponent],
  template: `
    <app-navbar></app-navbar>
    <div style="min-height: 80vh; background: #0d0d0d; color: #ffffff; padding: 2rem 1.5rem; max-width: 1100px; margin: 0 auto;">
      <div *ngIf="loading" style="text-align: center; color: var(--gold); padding: 4rem;">
        Cargando detalles de la suite…
      </div>

      <div *ngIf="!loading && suite">
        <a routerLink="/" style="color: var(--gold); text-decoration: none; font-size: 0.9rem;">← Volver al inicio</a>
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 2.5rem; margin-top: 1.5rem;">
          <div>
            <img [src]="suite.mainImage || 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800'" [alt]="suite.name" style="width: 100%; height: 380px; object-fit: cover; border-radius: 12px; border: 1px solid rgba(212, 175, 55, 0.2);">
            <h1 style="font-family: var(--font-title); font-size: 2rem; margin: 1.5rem 0 0.5rem 0;">{{suite.name}}</h1>
            <p style="color: var(--gold); font-size: 0.95rem;">{{suite.type}} · {{suite.branch?.name || 'Centro Histórico'}}</p>
            <p style="color: #cccccc; line-height: 1.6; margin-top: 1rem;">{{suite.description}}</p>
          </div>

          <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid rgba(212, 175, 55, 0.25); border-radius: 12px; padding: 2rem;">
            <h3 style="color: var(--gold); margin: 0 0 1rem 0; font-size: 1.4rem;">Reservar esta Suite</h3>
            <div style="margin-bottom: 1.5rem;">
              <strong style="font-size: 1.8rem;">$ {{ (suite.basePrice || suite.pricePerNight || 450000) | number:'1.0-0' }}</strong>
              <span style="color: #aaa;"> / noche</span>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem; margin-bottom: 1.5rem;">
              <div>
                <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Check-in</label>
                <input type="date" [(ngModel)]="checkIn" style="width: 100%; padding: 10px; background: rgba(0,0,0,0.5); border: 1px solid rgba(212,175,55,0.3); color: #fff; border-radius: 6px;">
              </div>
              <div>
                <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Check-out</label>
                <input type="date" [(ngModel)]="checkOut" style="width: 100%; padding: 10px; background: rgba(0,0,0,0.5); border: 1px solid rgba(212,175,55,0.3); color: #fff; border-radius: 6px;">
              </div>
              <div>
                <label style="display: block; font-size: 0.85rem; color: #aaa; margin-bottom: 4px;">Huéspedes</label>
                <select [(ngModel)]="guests" style="width: 100%; padding: 10px; background: rgba(0,0,0,0.5); border: 1px solid rgba(212,175,55,0.3); color: #fff; border-radius: 6px;">
                  <option [value]="1">1 Huésped</option>
                  <option [value]="2">2 Huéspedes</option>
                  <option [value]="3">3 Huéspedes</option>
                  <option [value]="4">4 Huéspedes</option>
                </select>
              </div>
            </div>

            <button (click)="createBooking()" [disabled]="bookingBusy" style="width: 100%; background: var(--gold); color: #0d0d0d; font-weight: 700; padding: 14px; border: none; border-radius: 6px; font-size: 1rem; cursor: pointer;">
              {{ bookingBusy ? 'Procesando…' : 'Confirmar Reserva' }}
            </button>
            <p *ngIf="message" style="margin-top: 1rem; text-align: center; font-size: 0.9rem;" [style.color]="isError ? '#ff4d4d' : 'var(--gold)'">{{message}}</p>
          </div>
        </div>
      </div>
    </div>
    <app-footer></app-footer>
  `
})
export class SuiteDetailComponent implements OnInit {
  suite: Suite | null = null;
  loading = true;
  bookingBusy = false;
  checkIn = new Date().toISOString().slice(0, 10);
  checkOut = new Date(Date.now() + 86400000 * 2).toISOString().slice(0, 10);
  guests = 2;
  message = '';
  isError = false;

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private auth: AuthService
  ) {}

  ngOnInit(): void {
    const idOrSlug = this.route.snapshot.paramMap.get('idOrSlug');
    if (idOrSlug) {
      this.api.get<any>(`/suites/${idOrSlug}`).subscribe({
        next: (res) => {
          this.suite = res.data || res;
          this.loading = false;
        },
        error: () => {
          this.loading = false;
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
