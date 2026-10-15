import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse } from '../../models/types';

export interface HostPromotionItem {
  id: string;
  _id?: string;
  title: string;
  branch?: string;
  branchId?: string;
  discountPercent: number;
  startDate: string;
  endDate: string;
  code?: string;
  active: boolean;
  createdByRole?: string;
}

@Component({
  selector: 'app-host-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1400px] mx-auto">
        
        <div class="flex flex-wrap justify-between items-center mb-8 gap-4">
          <div>
            <h1 class="font-serif text-4xl text-[#c9a96e]">Panel del Anfitrión</h1>
            <p class="text-[#f5f0e8] opacity-70 text-xs tracking-widest uppercase mt-1">Gestión Operativa de Sucursal</p>
          </div>
          <!-- Tabs Switcher -->
          <div class="flex gap-2 bg-[#111] p-1.5 rounded-lg border border-[rgba(201,169,110,0.3)]">
            <button (click)="activeTab.set('bookings')" 
                    [ngClass]="activeTab() === 'bookings' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Reservas & Recepción
            </button>
            <button (click)="activeTab.set('promotions')" 
                    [ngClass]="activeTab() === 'promotions' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Promociones de mi Sede
            </button>
          </div>
        </div>

        <!-- Toast Feedback Banner -->
        <div *ngIf="actionMessage()" class="mb-6 p-4 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded-lg text-sm flex items-center justify-between">
          <span>✔ {{ actionMessage() }}</span>
          <button (click)="actionMessage.set('')" class="text-xs opacity-70 hover:opacity-100">✕</button>
        </div>
        <div *ngIf="actionError()" class="mb-6 p-4 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded-lg text-sm flex items-center justify-between">
          <span>✖ {{ actionError() }}</span>
          <button (click)="actionError.set('')" class="text-xs opacity-70 hover:opacity-100">✕</button>
        </div>

        <!-- ==================== TAB 1: RESERVAS & RECEPCIÓN ==================== -->
        <ng-container *ngIf="activeTab() === 'bookings'">
          <!-- KPI Cards -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <div class="bg-gradient-to-br from-[#111] to-[rgba(201,169,110,0.1)] border border-[rgba(201,169,110,0.3)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Check-ins Pendientes Hoy</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.pendingCheckins || 0 }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Reservas Activas de mi Sucursal</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.activeBookings || 0 }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Ingresos Generados</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">$ {{ metrics()?.totalRevenue || 0 | number:'1.0-0' }}</p>
            </div>
          </div>

          <!-- Filters & Actions -->
          <div class="flex flex-wrap gap-4 justify-between items-end mb-6 p-4 bg-[rgba(255,255,255,0.02)] border border-[rgba(201,169,110,0.1)] rounded-lg">
            <div class="flex gap-4 items-center">
              <div class="flex flex-col">
                <label class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Buscar Reserva</label>
                <div class="flex gap-2">
                  <input type="text" [ngModel]="searchQuery()" (ngModelChange)="searchQuery.set($event)" placeholder="Email o Código..." class="bg-[#111] border border-[rgba(255,255,255,0.1)] text-[#f5f0e8] rounded-md px-3 py-2 text-sm outline-none w-64 placeholder-[rgba(255,255,255,0.2)] focus:border-[#c9a96e] transition">
                  <button (click)="applySearch()" class="bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] hover:text-white transition px-4 py-2 rounded-md text-xs font-semibold tracking-widest uppercase border border-[rgba(201,169,110,0.5)]">Buscar</button>
                </div>
              </div>
            </div>
          </div>

          <div *ngIf="loading()" class="flex justify-center items-center py-20">
            <div class="w-12 h-12 border-4 border-[#c9a96e] border-t-transparent rounded-full animate-spin"></div>
          </div>

          <!-- Table -->
          <div *ngIf="!loading()" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] overflow-hidden">
            <div class="overflow-x-auto">
              <table class="w-full text-left border-collapse">
                <thead>
                  <tr class="bg-[rgba(201,169,110,0.1)] border-b border-[rgba(201,169,110,0.2)]">
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Reserva ID</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Huésped</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Llegada / Salida</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Suite</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">PIN Cerradura</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold text-right">Recepción / Gestión</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let b of bookings()" class="border-b border-[rgba(255,255,255,0.05)] hover:bg-[rgba(255,255,255,0.02)] transition-colors">
                    <td class="py-4 px-6 font-mono text-sm text-[#f5f0e8]">{{ b.bookingCode || b.id || b._id }}</td>
                    <td class="py-4 px-6">
                      <p class="text-sm text-[#f5f0e8] font-medium">{{ b.guestName || b.guestEmail || 'Cliente' }}</p>
                      <p class="text-[10px] text-[#f5f0e8] opacity-50">{{ b.guestPhone || b.guestEmail }}</p>
                    </td>
                    <td class="py-4 px-6 text-sm text-[#f5f0e8] opacity-80">{{ b.checkIn | date:'dd/MM' }} &rarr; {{ b.checkOut | date:'dd/MM' }}</td>
                    <td class="py-4 px-6 text-sm text-[#f5f0e8]">{{ b.suite?.name || 'Suite' }}</td>
                    <td class="py-4 px-6">
                      <div class="flex items-center gap-2">
                        <span class="font-mono text-sm px-2 py-0.5 bg-[rgba(255,255,255,0.08)] border border-[rgba(201,169,110,0.3)] text-[#c9a96e] rounded">
                          {{ b.smartLockPin || '----' }}
                        </span>
                        <button (click)="regeneratePin(b)" title="Regenerar PIN" class="text-xs text-[#f5f0e8] opacity-60 hover:opacity-100 hover:text-[#c9a96e]">🔄</button>
                      </div>
                    </td>
                    <td class="py-4 px-6 text-right">
                      <div class="flex items-center justify-end gap-2">
                        <button *ngIf="!b.checkedIn" (click)="markCheckIn(b)" class="px-2.5 py-1 bg-[rgba(46,204,113,0.15)] text-[#2ecc71] hover:bg-[#2ecc71] hover:text-[#0d0d0d] rounded text-xs font-semibold transition border border-[#2ecc71]">
                          Check-in
                        </button>
                        <button *ngIf="b.checkedIn && !b.checkedOut" (click)="markCheckOut(b)" class="px-2.5 py-1 bg-[rgba(230,126,34,0.15)] text-[#e67e22] hover:bg-[#e67e22] hover:text-[#0d0d0d] rounded text-xs font-semibold transition border border-[#e67e22]">
                          Check-out
                        </button>
                        <a [routerLink]="['/reservas', b.id || b._id]" class="bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] hover:text-white transition px-3 py-1 rounded text-xs font-semibold tracking-wider uppercase border border-[rgba(201,169,110,0.5)]">
                          Gestionar
                        </a>
                      </div>
                    </td>
                  </tr>
                  <tr *ngIf="bookings().length === 0">
                    <td colspan="6" class="py-12 text-center text-[#f5f0e8] opacity-50 text-sm">No hay reservas recientes para tu sucursal.</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Server-side Pagination Controls -->
            <div class="bg-[rgba(255,255,255,0.02)] border-t border-[rgba(201,169,110,0.2)] p-4 flex justify-between items-center">
              <p class="text-xs text-[#f5f0e8] opacity-60">
                Mostrando página {{ currentPage() }} de {{ totalPages() }} (Total: {{ totalItems() }})
              </p>
              <div class="flex gap-2">
                <button [disabled]="currentPage() === 1" (click)="changePage(currentPage() - 1)" class="px-3 py-1 bg-[#111] border border-[rgba(201,169,110,0.3)] rounded text-xs text-[#c9a96e] disabled:opacity-30 disabled:cursor-not-allowed hover:bg-[rgba(201,169,110,0.1)] transition">Anterior</button>
                <button [disabled]="currentPage() === totalPages() || totalPages() === 0" (click)="changePage(currentPage() + 1)" class="px-3 py-1 bg-[#111] border border-[rgba(201,169,110,0.3)] rounded text-xs text-[#c9a96e] disabled:opacity-30 disabled:cursor-not-allowed hover:bg-[rgba(201,169,110,0.1)] transition">Siguiente</button>
              </div>
            </div>
          </div>
        </ng-container>

        <!-- ==================== TAB 2: PROMOTIONS DE SUCURSAL ==================== -->
        <ng-container *ngIf="activeTab() === 'promotions'">
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            
            <!-- Create Promo Form for Host -->
            <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 lg:col-span-1">
              <h2 class="font-serif text-2xl text-[#c9a96e] mb-4">Promoción Local de Sede</h2>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Lanza una promoción con rango de fechas para estimular la ocupación en tu sucursal.</p>

              <form (ngSubmit)="submitPromotion()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Título de la Oferta *</label>
                  <input type="text" [(ngModel)]="newPromo.title" name="promoTitle" required placeholder="Ej: Fin de Semana Bohemio en Getsemaní" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Sucursal Asignada</label>
                  <select [(ngModel)]="newPromo.branchId" name="promoBranch" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option value="branch-1">Palacio del Mar · Centro Histórico</option>
                    <option value="branch-2">Palacio del Mar · Getsemaní</option>
                    <option value="branch-3">Palacio del Mar · Bocagrande</option>
                    <option value="branch-4">Palacio del Mar · La Boquilla</option>
                  </select>
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Fecha Inicio *</label>
                    <input type="date" [(ngModel)]="newPromo.startDate" name="promoStartDate" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                  </div>
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Fecha Fin *</label>
                    <input type="date" [(ngModel)]="newPromo.endDate" name="promoEndDate" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                  </div>
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Descuento (%) *</label>
                    <input type="number" min="5" max="50" [(ngModel)]="newPromo.discountPercent" name="promoDiscount" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                  </div>
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Código Cupón</label>
                    <input type="text" [(ngModel)]="newPromo.code" name="promoCode" placeholder="Ej: HOST15" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] uppercase outline-none focus:border-[#c9a96e]">
                  </div>
                </div>

                <div *ngIf="promoError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ promoError }}
                </div>
                <div *ngIf="promoSuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ promoSuccess }}
                </div>

                <button type="submit" [disabled]="submittingPromo" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingPromo ? 'Publicando...' : 'Publicar Oferta de Sede' }}
                </button>
              </form>
            </div>

            <!-- List of Branch Promos -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h2 class="font-serif text-2xl text-[#c9a96e]">Ofertas Activas de Sede ({{ promotions().length }})</h2>
                <button (click)="fetchPromotions()" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="promotions().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No hay promociones locales registradas para tu sede en este momento.
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div *ngFor="let p of promotions()" class="bg-[#111] border border-[rgba(201,169,110,0.25)] hover:border-[#c9a96e] rounded-xl p-5 transition flex flex-col justify-between">
                  <div>
                    <div class="flex justify-between items-start gap-2 mb-2">
                      <h3 class="font-semibold text-lg text-[#f5f0e8]">{{ p.title }}</h3>
                      <span class="px-2 py-0.5 bg-[rgba(201,169,110,0.2)] text-[#c9a96e] font-bold text-xs rounded-full">
                        -{{ p.discountPercent }}%
                      </span>
                    </div>

                    <div class="space-y-1 text-xs text-[#f5f0e8] opacity-75 mb-4">
                      <p><strong class="text-[#c9a96e]">Vigencia:</strong> {{ p.startDate }} &rarr; {{ p.endDate }}</p>
                      <p *ngIf="p.code"><strong class="text-[#c9a96e]">Código:</strong> <span class="font-mono bg-[rgba(255,255,255,0.05)] px-2 py-0.5 rounded">{{ p.code }}</span></p>
                    </div>
                  </div>

                  <div class="flex justify-between items-center pt-3 border-t border-[rgba(255,255,255,0.08)]">
                    <span class="text-[10px] uppercase tracking-widest text-[#2ecc71] font-semibold">● Activa</span>
                    <button (click)="deletePromotion(p.id || p._id || '')" class="text-xs text-[#e74c3c] hover:underline transition">
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </ng-container>

      </div>
    </div>
  `
})
export class HostDashboardComponent implements OnInit {
  api = inject(ApiService);
  
  activeTab = signal<'bookings' | 'promotions'>('bookings');

  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  promotions = signal<HostPromotionItem[]>([]);
  loading = signal(true);
  actionMessage = signal<string>('');
  actionError = signal<string>('');

  // Pagination & Filters state
  currentPage = signal<number>(1);
  limit = signal<number>(10);
  totalPages = signal<number>(1);
  totalItems = signal<number>(0);

  searchQuery = signal<string>('');

  // Host Promotion Form
  newPromo = {
    title: '',
    branchId: 'branch-1',
    startDate: '',
    endDate: '',
    discountPercent: 15,
    code: ''
  };
  submittingPromo = false;
  promoError = '';
  promoSuccess = '';

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
    this.fetchPromotions();
  }

  fetchMetrics() {
    this.api.get<ApiResponse<DashboardMetricsDTO>>('/host/metrics').subscribe({
      next: (res) => {
        if (res.data) {
          this.metrics.set(res.data);
        }
      }
    });
  }

  fetchBookings() {
    this.loading.set(true);
    const params: any = {
      page: this.currentPage(),
      limit: this.limit()
    };
    if (this.searchQuery()) params.search = this.searchQuery();

    this.api.get<ApiResponse<PaginatedResponse<Booking>> | PaginatedResponse<Booking>>('/host/bookings', params).subscribe({
      next: (res) => {
        const payload: any = (res as any)?.data ?? res;
        if (payload?.content && Array.isArray(payload.content)) {
          this.bookings.set(payload.content);
          this.currentPage.set((payload.number ?? 0) + 1);
          this.totalPages.set(payload.totalPages ?? 1);
          this.totalItems.set(payload.totalElements ?? payload.content.length);
        } else if (payload?.data && Array.isArray(payload.data)) {
          this.bookings.set(payload.data);
          this.currentPage.set(payload.page || 1);
          this.totalPages.set(payload.totalPages || 1);
          this.totalItems.set(payload.total || payload.data.length);
        } else if (Array.isArray(payload)) {
          this.bookings.set(payload);
          this.totalPages.set(1);
          this.totalItems.set(payload.length);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.bookings.set([]);
      }
    });
  }

  fetchPromotions() {
    this.api.get<any>('/host/promotions').subscribe({
      next: (res) => {
        const list = res.data || res || [];
        this.promotions.set(Array.isArray(list) ? list : []);
      }
    });
  }

  submitPromotion() {
    this.promoError = '';
    this.promoSuccess = '';

    if (!this.newPromo.title || !this.newPromo.startDate || !this.newPromo.endDate) {
      this.promoError = 'Por favor completa el título y rango de fechas';
      return;
    }

    if (this.newPromo.endDate < this.newPromo.startDate) {
      this.promoError = 'La fecha de fin debe ser posterior a la fecha de inicio';
      return;
    }

    if (this.newPromo.discountPercent < 5 || this.newPromo.discountPercent > 50) {
      this.promoError = 'El porcentaje de descuento debe estar entre 5% y 50%';
      return;
    }

    this.submittingPromo = true;
    this.api.post<any>('/host/promotions', this.newPromo).subscribe({
      next: () => {
        this.submittingPromo = false;
        this.promoSuccess = '¡Oferta publicada exitosamente para tu sede!';
        this.newPromo = {
          title: '',
          branchId: 'branch-1',
          startDate: '',
          endDate: '',
          discountPercent: 15,
          code: ''
        };
        this.fetchPromotions();
      },
      error: (err) => {
        this.submittingPromo = false;
        this.promoError = err?.error?.message || 'Error al crear la promoción';
      }
    });
  }

  deletePromotion(id: string) {
    if (!id || !confirm('¿Estás seguro de eliminar esta promoción?')) return;
    this.api.delete<any>(`/host/promotions/${id}`).subscribe({
      next: () => {
        this.fetchPromotions();
      }
    });
  }

  markCheckIn(b: Booking) {
    const id = b.id || b._id;
    this.api.post<any>(`/host/bookings/${id}/checkin`, {}).subscribe({
      next: () => {
        b.checkedIn = true;
        this.fetchMetrics();
        this.actionMessage.set(`Check-in registrado con éxito para ${b.guestName || 'el huésped'}.`);
        setTimeout(() => this.actionMessage.set(''), 4000);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al registrar check-in');
        setTimeout(() => this.actionError.set(''), 5000);
      }
    });
  }

  markCheckOut(b: Booking) {
    const id = b.id || b._id;
    this.api.post<any>(`/host/bookings/${id}/checkout`, {}).subscribe({
      next: () => {
        b.checkedOut = true;
        b.status = 'completed';
        this.fetchMetrics();
        this.actionMessage.set('Check-out completado. Habitación liberada para aseo.');
        setTimeout(() => this.actionMessage.set(''), 4000);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al registrar check-out');
        setTimeout(() => this.actionError.set(''), 5000);
      }
    });
  }

  regeneratePin(b: Booking) {
    const id = b.id || b._id;
    this.api.post<any>(`/host/bookings/${id}/regenerate-pin`, {}).subscribe({
      next: (res) => {
        const newPin = res?.smartLockPin || res?.data?.smartLockPin;
        if (newPin) {
          b.smartLockPin = newPin;
          this.actionMessage.set(`PIN regenerado exitosamente: ${newPin}`);
          setTimeout(() => this.actionMessage.set(''), 4000);
        }
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al regenerar PIN');
        setTimeout(() => this.actionError.set(''), 5000);
      }
    });
  }

  applySearch() {
    this.currentPage.set(1);
    this.fetchBookings();
  }

  changePage(page: number) {
    if (page >= 1 && page <= this.totalPages()) {
      this.currentPage.set(page);
      this.fetchBookings();
    }
  }
}
