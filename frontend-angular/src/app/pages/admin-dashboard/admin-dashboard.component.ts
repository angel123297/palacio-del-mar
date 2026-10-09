import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse } from '../../models/types';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1400px] mx-auto">
        <h1 class="font-serif text-4xl text-[#c9a96e] mb-8">Panel de Administración</h1>

        <!-- KPI Cards -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Reservas Confirmadas</p>
            <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.activeBookings || 0 }}</p>
          </div>
          <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Ingresos Totales</p>
            <p class="font-serif text-4xl text-[#f5f0e8]">$ {{ metrics()?.totalRevenue || 0 | number:'1.0-0' }}</p>
          </div>
          <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Ocupación Promedio</p>
            <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.averageOccupancy || 0 }}%</p>
          </div>
          <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
            <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Total Reservas</p>
            <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.totalBookings || 0 }}</p>
          </div>
        </div>

        <!-- Filters & Actions -->
        <div class="flex flex-wrap gap-4 justify-between items-end mb-6 p-4 bg-[rgba(255,255,255,0.02)] border border-[rgba(201,169,110,0.1)] rounded-lg">
          <div class="flex gap-4 items-center">
            <div class="flex flex-col">
              <label class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Estado</label>
              <select [ngModel]="statusFilter()" (ngModelChange)="updateStatus($event)" class="bg-[#111] border border-[rgba(255,255,255,0.1)] text-[#f5f0e8] rounded-md px-3 py-2 text-sm outline-none">
                <option value="">Todos</option>
                <option value="pending">Pendientes</option>
                <option value="confirmed">Confirmadas</option>
                <option value="cancelled">Canceladas</option>
              </select>
            </div>
            <div class="flex flex-col">
              <label class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Buscar</label>
              <div class="flex gap-2">
                <input type="text" [ngModel]="searchQuery()" (ngModelChange)="searchQuery.set($event)" placeholder="Email o Código..." class="bg-[#111] border border-[rgba(255,255,255,0.1)] text-[#f5f0e8] rounded-md px-3 py-2 text-sm outline-none w-64 placeholder-[rgba(255,255,255,0.2)]">
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
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Código</th>
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Cliente</th>
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Fechas</th>
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Total</th>
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Estado</th>
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold text-right">Acciones</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let b of bookings()" class="border-b border-[rgba(255,255,255,0.05)] hover:bg-[rgba(255,255,255,0.02)] transition-colors">
                  <td class="py-4 px-6 font-mono text-sm text-[#f5f0e8]">{{ b.bookingCode || b.id || b._id }}</td>
                  <td class="py-4 px-6">
                    <p class="text-sm text-[#f5f0e8] font-medium">{{ b.guestName || b.guestEmail || 'Cliente' }}</p>
                    <p class="text-[10px] text-[#f5f0e8] opacity-50">{{ b.guestEmail }}</p>
                  </td>
                  <td class="py-4 px-6 text-sm text-[#f5f0e8] opacity-80">{{ b.checkIn | date:'dd/MM' }} &rarr; {{ b.checkOut | date:'dd/MM' }}</td>
                  <td class="py-4 px-6 font-serif text-lg text-[#c9a96e]">$ {{ b.totalPrice | number:'1.0-0' }}</td>
                  <td class="py-4 px-6">
                    <span class="px-3 py-1 rounded-full text-[10px] uppercase tracking-widest font-bold"
                          [ngClass]="{
                            'bg-[rgba(46,204,113,0.15)] text-[#2ecc71]': b.status === 'confirmed' || b.status === 'CONFIRMED',
                            'bg-[rgba(241,196,15,0.15)] text-[#f1c40f]': b.status === 'pending' || b.status === 'PENDING',
                            'bg-[rgba(231,76,60,0.15)] text-[#e74c3c]': b.status === 'cancelled' || b.status === 'CANCELLED'
                          }">
                      {{ b.status }}
                    </span>
                  </td>
                  <td class="py-4 px-6 text-right">
                    <button class="text-xs text-[#c9a96e] hover:text-white transition px-2">Ver</button>
                    <button *ngIf="b.status === 'pending' || b.status === 'PENDING'" class="text-xs text-[#2ecc71] hover:text-white transition px-2">Aprobar</button>
                  </td>
                </tr>
                <tr *ngIf="bookings().length === 0">
                  <td colspan="6" class="py-12 text-center text-[#f5f0e8] opacity-50 text-sm">No hay reservas que coincidan con los filtros.</td>
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
      </div>
    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  api = inject(ApiService);
  
  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  loading = signal(true);

  // Pagination & Filters state
  currentPage = signal<number>(1);
  limit = signal<number>(10);
  totalPages = signal<number>(1);
  totalItems = signal<number>(0);
  
  statusFilter = signal<string>('');
  searchQuery = signal<string>('');

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
  }

  fetchMetrics() {
    this.api.get<ApiResponse<DashboardMetricsDTO>>('/admin/metrics').subscribe({
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
    if (this.statusFilter()) params.status = this.statusFilter();
    if (this.searchQuery()) params.search = this.searchQuery();

    this.api.get<ApiResponse<PaginatedResponse<Booking>> | PaginatedResponse<Booking>>('/admin/bookings', params).subscribe({
      next: (res) => {
        const payload = (res as ApiResponse<PaginatedResponse<Booking>>).data || (res as PaginatedResponse<Booking>);
        if (payload && payload.data) {
          this.bookings.set(payload.data);
          this.currentPage.set(payload.page || 1);
          this.totalPages.set(payload.totalPages || 1);
          this.totalItems.set(payload.total || 0);
        } else if (Array.isArray(res)) {
          // Fallback just in case backend isn't updated yet
          this.bookings.set(res as any);
          this.totalPages.set(1);
          this.totalItems.set(res.length);
        } else if ((res as any).data && Array.isArray((res as any).data)) {
          this.bookings.set((res as any).data);
          this.totalPages.set(1);
          this.totalItems.set((res as any).data.length);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.bookings.set([]);
      }
    });
  }

  updateStatus(status: string) {
    this.statusFilter.set(status);
    this.currentPage.set(1);
    this.fetchBookings();
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


