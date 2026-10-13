import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse } from '../../models/types';

@Component({
  selector: 'app-host-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1400px] mx-auto">
        <h1 class="font-serif text-4xl text-[#c9a96e] mb-2">Panel del Anfitrión</h1>
        <p class="text-[#f5f0e8] opacity-70 mb-8 text-sm tracking-widest uppercase">Gestión de Sucursal</p>

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
                  <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold text-right">Acción Rápida</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let b of bookings()" class="border-b border-[rgba(255,255,255,0.05)] hover:bg-[rgba(255,255,255,0.02)] transition-colors">
                  <td class="py-4 px-6 font-mono text-sm text-[#f5f0e8]">{{ b.bookingCode || b.id || b._id }}</td>
                  <td class="py-4 px-6">
                    <p class="text-sm text-[#f5f0e8] font-medium">{{ b.guestName || b.guestEmail || 'Cliente' }}</p>
                  </td>
                  <td class="py-4 px-6 text-sm text-[#f5f0e8] opacity-80">{{ b.checkIn | date:'shortDate' }} &rarr; {{ b.checkOut | date:'shortDate' }}</td>
                  <td class="py-4 px-6 text-sm text-[#f5f0e8]">{{ b.suite?.name || 'Suite' }}</td>
                  <td class="py-4 px-6 text-right">
                    <button class="bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] hover:text-white transition px-4 py-1 rounded-full text-xs font-semibold tracking-widest uppercase border border-[rgba(201,169,110,0.5)]">
                      Gestionar
                    </button>
                  </td>
                </tr>
                <tr *ngIf="bookings().length === 0">
                  <td colspan="5" class="py-12 text-center text-[#f5f0e8] opacity-50 text-sm">No hay reservas recientes para tu sucursal.</td>
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
export class HostDashboardComponent implements OnInit {
  api = inject(ApiService);
  
  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  loading = signal(true);

  // Pagination & Filters state
  currentPage = signal<number>(1);
  limit = signal<number>(10);
  totalPages = signal<number>(1);
  totalItems = signal<number>(0);

  searchQuery = signal<string>('');

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
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

