import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse, Property } from '../../models/types';

export interface PromotionItem {
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
  createdAt?: string;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-[#0d0d0d] text-white pt-24 pb-16 px-6">
      <div class="max-w-[1400px] mx-auto">
        <div class="flex flex-wrap justify-between items-center mb-8 gap-4">
          <div>
            <h1 class="font-serif text-4xl text-[#c9a96e]">Panel de Administración</h1>
            <p class="text-xs text-[#f5f0e8] opacity-60 tracking-widest uppercase mt-1">Gestión Central Palacio del Mar</p>
          </div>
          <!-- Tabs Switcher -->
          <div class="flex gap-2 bg-[#111] p-1.5 rounded-lg border border-[rgba(201,169,110,0.3)]">
            <button (click)="activeTab.set('bookings')" 
                    [ngClass]="activeTab() === 'bookings' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Reservas
            </button>
            <button (click)="activeTab.set('promotions')" 
                    [ngClass]="activeTab() === 'promotions' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Promociones & Ofertas
            </button>
            <button (click)="activeTab.set('properties')" 
                    [ngClass]="activeTab() === 'properties' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition flex items-center gap-2">
              Alojamientos / Marketplace
              <span *ngIf="pendingPropertiesCount() > 0" class="bg-[#f1c40f] text-[#0d0d0d] text-[10px] px-2 py-0.5 rounded-full font-bold">
                {{ pendingPropertiesCount() }}
              </span>
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

        <!-- ==================== TAB 1: RESERVAS ==================== -->
        <ng-container *ngIf="activeTab() === 'bookings'">
          <!-- KPI Cards -->
          <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Total Usuarios</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.totalUsers || 0 }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Ingresos Netos</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">$ {{ metrics()?.totalRevenue || 0 | number:'1.0-0' }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Total Habitaciones</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.totalSuites || 0 }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Total Reservas</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ metrics()?.totalBookings || 0 }}</p>
            </div>
          </div>

          <!-- Filters & Actions -->
          <div class="flex flex-wrap gap-4 justify-between items-end mb-6 p-4 bg-[rgba(255,255,255,0.02)] border border-[rgba(201,169,110,0.1)] rounded-lg">
            <div class="flex flex-wrap gap-4 items-center">
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

            <!-- Export CSV Action -->
            <button (click)="exportCSV()" class="bg-[#111] hover:bg-[rgba(201,169,110,0.2)] text-[#c9a96e] hover:text-white transition px-5 py-2.5 rounded-md text-xs font-semibold tracking-widest uppercase border border-[rgba(201,169,110,0.4)] flex items-center gap-2">
              <span>📥</span> Exportar CSV
            </button>
          </div>

          <div *ngIf="loading()" class="flex justify-center items-center py-20">
            <div class="w-12 h-12 border-4 border-[#c9a96e] border-t-transparent rounded-full animate-spin"></div>
          </div>

          <!-- Bookings Table -->
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
                      <button [routerLink]="['/reservas', b.id || b._id]" class="text-xs text-[#c9a96e] hover:text-white transition px-2">Ver</button>
                      <button *ngIf="b.status === 'pending' || b.status === 'PENDING'" 
                              (click)="approveBooking(b)" 
                              class="text-xs text-[#2ecc71] hover:text-white transition px-2 font-semibold border border-[#2ecc71] rounded py-1 bg-[rgba(46,204,113,0.1)]">
                        Aprobar
                      </button>
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
        </ng-container>

        <!-- ==================== TAB 2: PROMOTIONS MODULE ==================== -->
        <ng-container *ngIf="activeTab() === 'promotions'">
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            
            <!-- Form Card: Create Promotion -->
            <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 lg:col-span-1">
              <h2 class="font-serif text-2xl text-[#c9a96e] mb-4">Nueva Promoción</h2>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Configura un rango de fechas con descuento promocional y título atractivo para impulsar reservas.</p>

              <form (ngSubmit)="submitPromotion()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Título de la Promoción *</label>
                  <input type="text" [(ngModel)]="newPromo.title" name="promoTitle" required placeholder="Ej: Escapada Romántica de Mitad de Semana" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Sede de Aplicación</label>
                  <select [(ngModel)]="newPromo.branchId" name="promoBranch" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option value="all">Todas las sedes (Global)</option>
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
                    <input type="text" [(ngModel)]="newPromo.code" name="promoCode" placeholder="Ej: DESC20" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] uppercase outline-none focus:border-[#c9a96e]">
                  </div>
                </div>

                <div *ngIf="promoError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ promoError }}
                </div>
                <div *ngIf="promoSuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ promoSuccess }}
                </div>

                <button type="submit" [disabled]="submittingPromo" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingPromo ? 'Publicando...' : 'Publicar Promoción' }}
                </button>
              </form>
            </div>

            <!-- List Card: Active Promotions -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h2 class="font-serif text-2xl text-[#c9a96e]">Promociones Activas ({{ promotions().length }})</h2>
                <button (click)="fetchPromotions()" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="promotions().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No hay promociones activas registradas. ¡Crea una desde el formulario lateral!
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
                      <p><strong class="text-[#c9a96e]">Sede:</strong> {{ getBranchName(p.branchId || p.branch) }}</p>
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

        <!-- ==================== TAB 3: ALOJAMIENTOS / MARKETPLACE ==================== -->
        <ng-container *ngIf="activeTab() === 'properties'">
          <!-- KPI Cards for Properties -->
          <div class="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Total Alojamientos</p>
              <p class="font-serif text-4xl text-[#f5f0e8]">{{ properties().length }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">En Revisión (Pendientes)</p>
              <p class="font-serif text-4xl" [ngClass]="pendingPropertiesCount() > 0 ? 'text-[#f1c40f]' : 'text-[#f5f0e8]'">
                {{ pendingPropertiesCount() }}
              </p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Aprobados / En Vivo</p>
              <p class="font-serif text-4xl text-[#2ecc71]">{{ approvedPropertiesCount() }}</p>
            </div>
            <div class="bg-[#111] border border-[rgba(201,169,110,0.2)] rounded-xl p-6">
              <p class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-2">Comisión Plataforma</p>
              <p class="font-serif text-4xl text-[#c9a96e]">8%</p>
            </div>
          </div>

          <!-- Filters & Actions -->
          <div class="flex flex-wrap gap-4 justify-between items-end mb-6 p-4 bg-[rgba(255,255,255,0.02)] border border-[rgba(201,169,110,0.1)] rounded-lg">
            <div class="flex flex-wrap gap-4 items-center">
              <div class="flex flex-col">
                <label class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Estado de Aprobación</label>
                <select [ngModel]="propertyStatusFilter()" (ngModelChange)="propertyStatusFilter.set($event)" class="bg-[#111] border border-[rgba(255,255,255,0.1)] text-[#f5f0e8] rounded-md px-3 py-2 text-sm outline-none">
                  <option value="">Todos los alojamientos</option>
                  <option value="PENDING">Pendientes de Revisión (Requieren Aprobación)</option>
                  <option value="APPROVED">Aprobados (Visibles al Público)</option>
                  <option value="REJECTED">Rechazados</option>
                </select>
              </div>
              <div class="flex flex-col">
                <label class="text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Buscar</label>
                <input type="text" [ngModel]="propertySearch()" (ngModelChange)="propertySearch.set($event)" placeholder="Nombre, ciudad o dirección..." class="bg-[#111] border border-[rgba(255,255,255,0.1)] text-[#f5f0e8] rounded-md px-3 py-2 text-sm outline-none w-72 placeholder-[rgba(255,255,255,0.2)]">
              </div>
            </div>

            <div class="flex items-center gap-3">
              <button (click)="fetchProperties()" class="bg-[#111] hover:bg-[rgba(201,169,110,0.2)] text-[#c9a96e] hover:text-white transition px-4 py-2.5 rounded-md text-xs font-semibold tracking-widest uppercase border border-[rgba(201,169,110,0.4)] flex items-center gap-2">
                <span>🔄</span> Actualizar
              </button>
              <a routerLink="/simulador-hotel-externo" target="_blank" class="bg-[rgba(201,169,110,0.15)] hover:bg-[rgba(201,169,110,0.3)] text-[#c9a96e] hover:text-white transition px-4 py-2.5 rounded-md text-xs font-semibold tracking-widest uppercase border border-[#c9a96e] flex items-center gap-2">
                <span>🏨</span> Simulador PMS
              </a>
            </div>
          </div>

          <!-- Properties Table -->
          <div class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] overflow-hidden">
            <div class="overflow-x-auto">
              <table class="w-full text-left border-collapse">
                <thead>
                  <tr class="bg-[rgba(201,169,110,0.1)] border-b border-[rgba(201,169,110,0.2)]">
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Alojamiento / Propiedad</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Ubicación</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Tipo</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Habitaciones</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Comisión</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold">Estado</th>
                    <th class="py-4 px-6 text-[11px] uppercase tracking-widest text-[#c9a96e] font-semibold text-right">Moderación</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let p of filteredProperties()" class="border-b border-[rgba(255,255,255,0.05)] hover:bg-[rgba(255,255,255,0.02)] transition-colors">
                    <td class="py-4 px-6">
                      <div class="flex items-center gap-3">
                        <div *ngIf="p.mainImage" class="w-10 h-10 rounded-lg bg-cover bg-center shrink-0 border border-[rgba(201,169,110,0.2)]" [style.background-image]="'url(' + p.mainImage + ')'"></div>
                        <div>
                          <p class="text-sm font-semibold text-[#f5f0e8]">{{ p.name }}</p>
                          <p class="text-[10px] text-[#f5f0e8] opacity-50 font-mono">ID: {{ p.id || p._id }}</p>
                        </div>
                      </div>
                    </td>
                    <td class="py-4 px-6 text-sm text-[#f5f0e8] opacity-80">
                      <p>{{ p.city || 'Cartagena' }}</p>
                      <p class="text-[10px] opacity-60">{{ p.address }}</p>
                    </td>
                    <td class="py-4 px-6">
                      <span class="px-2 py-0.5 rounded text-[10px] uppercase font-mono tracking-wider bg-[rgba(255,255,255,0.05)] border border-[rgba(255,255,255,0.1)] text-[#c9a96e]">
                        {{ p.type || 'HOTEL' }}
                      </span>
                    </td>
                    <td class="py-4 px-6 font-mono text-sm text-[#f5f0e8]">
                      {{ p.suitesCount || 0 }} suites
                    </td>
                    <td class="py-4 px-6 font-serif text-sm text-[#c9a96e]">
                      {{ p.commissionRate ? (p.commissionRate * 100 | number:'1.0-0') + '%' : '8%' }}
                    </td>
                    <td class="py-4 px-6">
                      <span class="px-3 py-1 rounded-full text-[10px] uppercase tracking-widest font-bold"
                            [ngClass]="{
                              'bg-[rgba(46,204,113,0.15)] text-[#2ecc71] border border-[#2ecc71]': p.status === 'APPROVED',
                              'bg-[rgba(241,196,15,0.15)] text-[#f1c40f] border border-[#f1c40f]': p.status === 'PENDING',
                              'bg-[rgba(231,76,60,0.15)] text-[#e74c3c] border border-[#e74c3c]': p.status === 'REJECTED'
                            }">
                        {{ p.status === 'APPROVED' ? 'Aprobado / En Vivo' : (p.status === 'PENDING' ? 'En Revisión' : 'Rechazado') }}
                      </span>
                    </td>
                    <td class="py-4 px-6 text-right">
                      <div class="flex items-center justify-end gap-2">
                        <!-- Action to Approve -->
                        <button *ngIf="p.status !== 'APPROVED'"
                                (click)="updatePropertyStatus(p.id || p._id || '', 'APPROVED')"
                                class="px-3 py-1.5 rounded text-xs font-semibold uppercase tracking-wider bg-[rgba(46,204,113,0.15)] hover:bg-[#2ecc71] text-[#2ecc71] hover:text-[#0d0d0d] border border-[#2ecc71] transition">
                          ✔ Aprobar
                        </button>
                        <!-- Action to Reject -->
                        <button *ngIf="p.status !== 'REJECTED'"
                                (click)="updatePropertyStatus(p.id || p._id || '', 'REJECTED')"
                                class="px-3 py-1.5 rounded text-xs font-semibold uppercase tracking-wider bg-[rgba(231,76,60,0.15)] hover:bg-[#e74c3c] text-[#e74c3c] hover:text-white border border-[#e74c3c] transition">
                          ✖ Rechazar
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr *ngIf="filteredProperties().length === 0">
                    <td colspan="7" class="py-12 text-center text-[#f5f0e8] opacity-50 text-sm">
                      No hay alojamientos que coincidan con los filtros seleccionados.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </ng-container>

      </div>
    </div>
  `
})
export class AdminDashboardComponent implements OnInit {
  api = inject(ApiService);
  
  activeTab = signal<'bookings' | 'promotions' | 'properties'>('bookings');

  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  promotions = signal<PromotionItem[]>([]);
  loading = signal(true);
  actionMessage = signal<string>('');
  actionError = signal<string>('');

  // Pagination & Filters state
  currentPage = signal<number>(1);
  limit = signal<number>(10);
  totalPages = signal<number>(1);
  totalItems = signal<number>(0);
  
  statusFilter = signal<string>('');
  searchQuery = signal<string>('');

  // Promotion Form State
  newPromo = {
    title: '',
    branchId: 'all',
    startDate: '',
    endDate: '',
    discountPercent: 15,
    code: ''
  };
  submittingPromo = false;
  promoError = '';
  promoSuccess = '';

  // Properties / Marketplace state
  properties = signal<Property[]>([]);
  propertyStatusFilter = signal<string>('');
  propertySearch = signal<string>('');
  
  pendingPropertiesCount = computed(() => this.properties().filter(p => p.status === 'PENDING').length);
  approvedPropertiesCount = computed(() => this.properties().filter(p => p.status === 'APPROVED').length);

  filteredProperties = computed(() => {
    let list = this.properties();
    const status = this.propertyStatusFilter();
    const query = this.propertySearch().toLowerCase().trim();
    if (status) {
      list = list.filter(p => p.status === status);
    }
    if (query) {
      list = list.filter(p => 
        (p.name || '').toLowerCase().includes(query) ||
        (p.city || '').toLowerCase().includes(query) ||
        (p.address || '').toLowerCase().includes(query)
      );
    }
    return list;
  });

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
    this.fetchPromotions();
    this.fetchProperties();
  }

  fetchMetrics() {
    this.api.get<ApiResponse<DashboardMetricsDTO>>('/admin/stats').subscribe({
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
        const payload: any = (res as any).data || res;
        
        if (payload && payload.content) {
          this.bookings.set(payload.content);
          this.currentPage.set((payload.number || 0) + 1);
          this.totalPages.set(payload.totalPages || 1);
          this.totalItems.set(payload.totalElements || 0);
        } else if (payload && payload.data && !Array.isArray(payload)) {
          this.bookings.set(payload.data);
          this.currentPage.set(payload.page || 1);
          this.totalPages.set(payload.totalPages || 1);
          this.totalItems.set(payload.total || 0);
        } else if (Array.isArray(payload)) {
          this.bookings.set(payload);
          this.totalPages.set(1);
          this.totalItems.set(payload.length);
        } else {
          this.bookings.set([]);
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
    this.api.get<any>('/promotions').subscribe({
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
    this.api.post<any>('/admin/promotions', this.newPromo).subscribe({
      next: () => {
        this.submittingPromo = false;
        this.promoSuccess = '¡Promoción creada exitosamente!';
        this.newPromo = {
          title: '',
          branchId: 'all',
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
    this.api.delete<any>(`/admin/promotions/${id}`).subscribe({
      next: () => {
        this.fetchPromotions();
      }
    });
  }

  approveBooking(b: Booking) {
    const id = b.id || b._id;
    this.api.post<any>(`/admin/bookings/${id}/approve`, {}).subscribe({
      next: () => {
        this.fetchBookings();
        this.fetchMetrics();
        this.actionMessage.set(`Reserva ${b.bookingCode || id} aprobada exitosamente.`);
        setTimeout(() => this.actionMessage.set(''), 4000);
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al aprobar la reserva');
        setTimeout(() => this.actionError.set(''), 5000);
      }
    });
  }

  exportCSV() {
    window.location.href = '/api/admin/bookings/export-csv';
  }

  getBranchName(id?: string): string {
    if (!id || id === 'all') return 'Todas las sedes (Global)';
    const map: Record<string, string> = {
      'branch-1': 'Centro Histórico',
      'branch-2': 'Getsemaní',
      'branch-3': 'Bocagrande',
      'branch-4': 'La Boquilla'
    };
    return map[id] || id;
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

  fetchProperties() {
    this.api.get<ApiResponse<Property[]>>('/admin/properties').subscribe({
      next: (res) => {
        const list = res.data || (res as any) || [];
        this.properties.set(Array.isArray(list) ? list : []);
      },
      error: () => {
        this.properties.set([]);
      }
    });
  }

  updatePropertyStatus(propertyId: string, status: 'APPROVED' | 'REJECTED') {
    if (!propertyId) return;
    this.api.put<ApiResponse<any>>(`/admin/properties/${propertyId}/status`, { status }).subscribe({
      next: () => {
        const actionText = status === 'APPROVED' ? 'aprobado y publicado en el marketplace' : 'rechazado';
        this.actionMessage.set(`Alojamiento ${actionText} exitosamente.`);
        setTimeout(() => this.actionMessage.set(''), 4000);
        this.fetchProperties();
        this.fetchMetrics();
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al actualizar estado del alojamiento');
        setTimeout(() => this.actionError.set(''), 5000);
      }
    });
  }
}
