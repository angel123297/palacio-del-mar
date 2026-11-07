import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { CurrencyService } from '../../services/currency.service';
import { Experience } from '../../models/types';

@Component({
  selector: 'app-experiences-section',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section id="experiences" class="py-16 px-4 md:px-12 bg-[#0d0d0d] relative overflow-hidden" aria-label="Experiencias Exclusivas en Cartagena">
      <div class="max-w-7xl mx-auto">
        <div class="text-center mb-12">
          <p class="text-[#c9a96e] uppercase tracking-widest text-xs font-semibold mb-2">Momentos Inolvidables</p>
          <h2 class="text-3xl md:text-4xl font-serif text-[#f5f0e8] mb-4">Experiencias de Lujo & Aventura</h2>
          <p class="text-[#aaa] max-w-2xl mx-auto text-sm md:text-base">
            Diseñadas para disfrutar con o sin hospedaje. Vive la magia colonial, la brisa del Caribe y la gastronomía de Cartagena de Indias con reserva inmediata.
          </p>
        </div>

        <!-- Skeleton loader -->
        <div *ngIf="loading" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div *ngFor="let i of [1,2,3,4]" class="animate-pulse bg-[#161311] border border-[rgba(255,255,255,0.06)] rounded-xl overflow-hidden h-80 flex flex-col">
            <div class="h-44 bg-[rgba(255,255,255,0.05)] w-full"></div>
            <div class="p-4 flex-1 flex flex-col justify-between">
              <div class="space-y-2">
                <div class="h-4 bg-[rgba(255,255,255,0.08)] rounded w-3/4"></div>
                <div class="h-3 bg-[rgba(255,255,255,0.04)] rounded w-full"></div>
              </div>
              <div class="h-4 bg-[rgba(201,169,110,0.15)] rounded w-1/2"></div>
            </div>
          </div>
        </div>

        <!-- Experiences Grid -->
        <div *ngIf="!loading && experiences.length > 0" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <div *ngFor="let exp of experiences" class="group bg-[#161311] border border-[rgba(201,169,110,0.2)] rounded-xl overflow-hidden shadow-lg hover:border-[#c9a96e] transition-all duration-300 flex flex-col">
            <div class="relative h-44 overflow-hidden">
              <img [src]="exp.image || exp.mainImage" [alt]="exp.name" loading="lazy" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
              <span class="absolute top-3 right-3 bg-[rgba(0,0,0,0.7)] text-[#c9a96e] text-[10px] tracking-wider uppercase px-2.5 py-1 rounded-full border border-[rgba(201,169,110,0.4)] backdrop-blur-sm">
                {{ exp.category || 'Exclusivo' }}
              </span>
            </div>
            <div class="p-4 flex-1 flex flex-col justify-between">
              <div>
                <h3 class="text-lg font-serif text-[#f5f0e8] mb-1 group-hover:text-[#c9a96e] transition-colors">{{ exp.name }}</h3>
                <p class="text-xs text-[#888] line-clamp-2 leading-relaxed mb-3">{{ exp.shortDescription || exp.description }}</p>
              </div>
              <div class="pt-3 border-t border-[rgba(255,255,255,0.05)] flex items-center justify-between">
                <div>
                  <span class="text-[10px] text-[#888] uppercase block">Por persona</span>
                  <span class="text-sm font-bold text-[#c9a96e]">{{ currencyService.formatPrice(exp.price) }}</span>
                </div>
                <button type="button" (click)="openBookingModal(exp)" class="text-xs uppercase tracking-wider text-[#f5f0e8] hover:text-[#c9a96e] bg-[rgba(201,169,110,0.15)] hover:bg-[rgba(201,169,110,0.25)] border border-[rgba(201,169,110,0.4)] px-3 py-1.5 rounded-lg transition-colors font-medium cursor-pointer">
                  Reservar →
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Quick Experience Booking Modal -->
      <div *ngIf="selectedExp" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[rgba(0,0,0,0.85)] backdrop-blur-sm animate-fade-in">
        <div class="bg-[#141210] border border-[rgba(201,169,110,0.3)] rounded-2xl max-w-lg w-full p-6 text-[#f5f0e8] shadow-2xl relative">
          <button (click)="closeBookingModal()" class="absolute top-4 right-4 text-[#888] hover:text-[#fff] text-xl font-bold cursor-pointer" aria-label="Cerrar modal">&times;</button>
          
          <div class="mb-4">
            <span class="text-[#c9a96e] text-xs font-semibold tracking-wider uppercase">Reserva de Actividad Exclusiva</span>
            <h3 class="text-2xl font-serif text-[#f5f0e8] mt-1">{{ selectedExp.name }}</h3>
            <p class="text-xs text-[#aaa] mt-1">{{ selectedExp.shortDescription || selectedExp.description }}</p>
          </div>

          <div *ngIf="errorMessage" class="mb-4 p-3 bg-red-950/50 border border-red-500/50 text-red-300 text-xs rounded-lg">
            {{ errorMessage }}
          </div>

          <form (ngSubmit)="submitExperienceBooking()" class="space-y-4">
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Fecha</label>
                <input type="date" [(ngModel)]="bookingDate" name="bookingDate" [min]="minDate" required class="w-full bg-[#1e1a17] border border-[rgba(255,255,255,0.1)] focus:border-[#c9a96e] rounded-lg px-3 py-2 text-sm text-[#f5f0e8] outline-none" />
              </div>
              <div>
                <label class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Personas</label>
                <input type="number" [(ngModel)]="bookingQty" name="bookingQty" min="1" max="10" required class="w-full bg-[#1e1a17] border border-[rgba(255,255,255,0.1)] focus:border-[#c9a96e] rounded-lg px-3 py-2 text-sm text-[#f5f0e8] outline-none" />
              </div>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Nombre Completo</label>
                <input type="text" [(ngModel)]="guestName" name="guestName" placeholder="Tu nombre" required class="w-full bg-[#1e1a17] border border-[rgba(255,255,255,0.1)] focus:border-[#c9a96e] rounded-lg px-3 py-2 text-sm text-[#f5f0e8] outline-none" />
              </div>
              <div>
                <label class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Correo Electrónico</label>
                <input type="email" [(ngModel)]="guestEmail" name="guestEmail" placeholder="tu@email.com" required class="w-full bg-[#1e1a17] border border-[rgba(255,255,255,0.1)] focus:border-[#c9a96e] rounded-lg px-3 py-2 text-sm text-[#f5f0e8] outline-none" />
              </div>
            </div>

            <div>
              <label class="block text-xs uppercase tracking-wider text-[#c9a96e] mb-1 font-semibold">Teléfono / WhatsApp</label>
              <input type="tel" [(ngModel)]="guestPhone" name="guestPhone" placeholder="+57 300 000 0000" class="w-full bg-[#1e1a17] border border-[rgba(255,255,255,0.1)] focus:border-[#c9a96e] rounded-lg px-3 py-2 text-sm text-[#f5f0e8] outline-none" />
            </div>

            <div class="pt-3 border-t border-[rgba(255,255,255,0.08)] flex items-center justify-between">
              <div>
                <span class="text-xs text-[#888] block">Total a Pagar</span>
                <span class="text-xl font-bold text-[#c9a96e]">{{ currencyService.formatPrice(selectedExp.price * (bookingQty || 1)) }}</span>
              </div>
              <button type="submit" [disabled]="submitting" class="bg-[#c9a96e] hover:bg-[#b89558] text-[#0d0d0d] font-bold text-xs uppercase tracking-wider px-6 py-2.5 rounded-lg transition-all shadow-md hover:shadow-lg disabled:opacity-50 cursor-pointer">
                {{ submitting ? 'Procesando...' : 'Ir al Pago Seguro →' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </section>
  `
})
export class ExperiencesSectionComponent implements OnInit {
  experiences: Experience[] = [];
  loading = true;
  selectedExp: Experience | null = null;
  bookingDate: string = '';
  bookingQty: number = 1;
  guestName: string = '';
  guestEmail: string = '';
  guestPhone: string = '';
  submitting = false;
  errorMessage = '';
  minDate = new Date().toISOString().split('T')[0];

  constructor(
    private api: ApiService,
    private auth: AuthService,
    public currencyService: CurrencyService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.api.get<any>('/experiences').subscribe({
      next: (res) => {
        const list = res.data || res || [];
        this.experiences = Array.isArray(list) ? list.slice(0, 4) : [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      }
    });

    const user = this.auth.currentUserValue;
    if (user) {
      this.guestName = user.name || '';
      this.guestEmail = user.email || '';
      this.guestPhone = user.phone || '';
    }
  }

  openBookingModal(exp: Experience): void {
    this.selectedExp = exp;
    this.errorMessage = '';
    this.bookingQty = 1;
    this.bookingDate = new Date().toISOString().split('T')[0];
    const user = this.auth.currentUserValue;
    if (user) {
      this.guestName = user.name || this.guestName;
      this.guestEmail = user.email || this.guestEmail;
      this.guestPhone = user.phone || this.guestPhone;
    }
    this.cdr.detectChanges();
  }

  closeBookingModal(): void {
    this.selectedExp = null;
    this.errorMessage = '';
    this.cdr.detectChanges();
  }

  submitExperienceBooking(): void {
    if (!this.selectedExp) return;

    if (!this.bookingDate) {
      this.errorMessage = 'Por favor selecciona la fecha de la actividad.';
      return;
    }
    if (!this.guestName.trim() || !this.guestEmail.trim()) {
      this.errorMessage = 'Por favor completa tu nombre y correo electrónico.';
      return;
    }

    this.submitting = true;
    this.errorMessage = '';

    const expId = this.selectedExp.id || (this.selectedExp as any)._id;
    const payload = {
      checkIn: this.bookingDate,
      checkOut: this.bookingDate,
      guests: Number(this.bookingQty) || 1,
      guestName: this.guestName.trim(),
      guestEmail: this.guestEmail.trim(),
      guestPhone: this.guestPhone.trim(),
      experiences: [
        {
          experienceId: expId,
          quantity: Number(this.bookingQty) || 1
        }
      ]
    };

    const idempotencyKey = `exp_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;

    this.api.post<any>('/bookings', payload, { 'Idempotency-Key': idempotencyKey }).subscribe({
      next: (res) => {
        this.submitting = false;
        const booking = res?.data || res;
        const bId = booking?.id || booking?._id;
        this.closeBookingModal();
        if (bId) {
          this.router.navigate(['/checkout', bId]);
        } else {
          this.router.navigate(['/mis-reservas']);
        }
      },
      error: (err) => {
        this.submitting = false;
        this.errorMessage = err?.error?.message || 'Error al generar la reserva de la experiencia. Por favor intenta de nuevo.';
        this.cdr.detectChanges();
      }
    });
  }
}
