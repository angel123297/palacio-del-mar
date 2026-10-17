import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse, Property, WebhookSubscription, WebhookDelivery, ApiKeyItem } from '../../models/types';

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
            <p class="text-[#f5f0e8] opacity-70 text-xs tracking-widest uppercase mt-1">Marketplace de Hospedaje</p>
          </div>
          <!-- Tabs Switcher -->
          <div class="flex gap-2 bg-[#111] p-1.5 rounded-lg border border-[rgba(201,169,110,0.3)]">
            <button (click)="activeTab.set('bookings')" 
                    [ngClass]="activeTab() === 'bookings' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Reservas & Recepción
            </button>
            <button (click)="activeTab.set('properties')" 
                    [ngClass]="activeTab() === 'properties' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Mis Alojamientos
            </button>
            <button (click)="activeTab.set('webhooks'); fetchSubscriptions(); fetchDeliveries();" 
                    [ngClass]="activeTab() === 'webhooks' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Notificaciones en Vivo
            </button>
            <button (click)="activeTab.set('apikeys'); fetchApiKeys();" 
                    [ngClass]="activeTab() === 'apikeys' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Conexión PMS / Integración
            </button>
            <button (click)="activeTab.set('promotions')" 
                    [ngClass]="activeTab() === 'promotions' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Promociones
            </button>
            <button (click)="activeTab.set('payouts'); fetchPayouts();" 
                    [ngClass]="activeTab() === 'payouts' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Liquidaciones (92%)
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
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Alojamiento Asignado *</label>
                  <select [(ngModel)]="newPromo.branchId" name="promoBranch" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option *ngIf="properties().length === 0" value="" disabled>No tienes alojamientos disponibles</option>
                    <option *ngFor="let prop of properties()" [value]="prop.id || prop._id">
                      {{ prop.name }} ({{ prop.city }})
                    </option>
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

        <!-- ==================== TAB 3: MIS ALOJAMIENTOS ==================== -->
        <ng-container *ngIf="activeTab() === 'properties'">
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            
            <!-- Register Property Form -->
            <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 h-fit">
              <h2 class="font-serif text-2xl text-[#c9a96e] mb-1">Publicar Alojamiento</h2>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Registra tu hotel, suite o habitación con comisión baja (8%)</p>

              <form (ngSubmit)="submitProperty()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Nombre del Alojamiento *</label>
                  <input type="text" [(ngModel)]="newProperty.name" name="propName" required placeholder="Ej: Cabaña Las Olas" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Tipo *</label>
                    <select [(ngModel)]="newProperty.type" name="propType" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                      <option value="HOTEL">Hotel</option>
                      <option value="ROOM">Habitación</option>
                      <option value="APARTMENT">Apartamento</option>
                      <option value="VILLA">Villa</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Ciudad *</label>
                    <input type="text" [(ngModel)]="newProperty.city" name="propCity" required placeholder="Cartagena" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                  </div>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Dirección *</label>
                  <input type="text" [(ngModel)]="newProperty.address" name="propAddress" required placeholder="Calle 30 # 8B-25, Getsemaní" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Descripción</label>
                  <textarea [(ngModel)]="newProperty.description" name="propDesc" rows="2" placeholder="Hermoso espacio frente al mar..." class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]"></textarea>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">URL Imagen Principal</label>
                  <input type="url" [(ngModel)]="newProperty.mainImage" name="propImg" placeholder="https://..." class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div *ngIf="propertyError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ propertyError }}
                </div>
                <div *ngIf="propertySuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ propertySuccess }}
                </div>

                <button type="submit" [disabled]="submittingProperty" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingProperty ? 'Guardando...' : 'Publicar Alojamiento' }}
                </button>
              </form>
            </div>

            <!-- List of My Properties -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h2 class="font-serif text-2xl text-[#c9a96e]">Mis Alojamientos ({{ properties().length }})</h2>
                <button (click)="fetchProperties()" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="properties().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No tienes alojamientos registrados todavía. ¡Publica tu primer hotel o habitación en el formulario de la izquierda!
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div *ngFor="let prop of properties()" class="bg-[#111] border border-[rgba(201,169,110,0.25)] hover:border-[#c9a96e] rounded-xl overflow-hidden transition flex flex-col justify-between">
                  <div *ngIf="prop.mainImage" class="h-36 w-full overflow-hidden bg-[#222]">
                    <img [src]="prop.mainImage" [alt]="prop.name" class="w-full h-full object-cover">
                  </div>
                  <div class="p-5 flex-1 flex flex-col justify-between">
                    <div>
                      <div class="flex justify-between items-start gap-2 mb-2">
                        <h3 class="font-semibold text-lg text-[#f5f0e8]">{{ prop.name }}</h3>
                        <span [ngClass]="prop.status === 'APPROVED' ? 'bg-[rgba(46,204,113,0.2)] text-[#2ecc71]' : 'bg-[rgba(241,196,15,0.2)] text-[#f1c40f]'" class="px-2.5 py-0.5 font-bold text-[10px] tracking-wider uppercase rounded-full">
                          {{ prop.status === 'APPROVED' ? '● Aprobado' : '⏳ Pendiente' }}
                        </span>
                      </div>
                      <p class="text-xs text-[#c9a96e] mb-1">📍 {{ prop.city }} &bull; {{ prop.address }}</p>
                      <p *ngIf="prop.description" class="text-xs text-[#f5f0e8] opacity-75 line-clamp-2 mb-3">{{ prop.description }}</p>
                    </div>

                    <div class="pt-3 border-t border-[rgba(255,255,255,0.08)] flex justify-between items-center text-xs">
                      <span class="text-[#f5f0e8] opacity-60">Comisión: <strong class="text-[#c9a96e]">8%</strong></span>
                      <button type="button" (click)="openSuitesModal(prop)" class="px-3 py-1 bg-[rgba(201,169,110,0.15)] hover:bg-[#c9a96e] text-[#c9a96e] hover:text-[#0d0d0d] font-semibold rounded border border-[rgba(201,169,110,0.4)] transition flex items-center gap-1.5 cursor-pointer">
                        <span>🛌</span> Habitaciones ({{ prop.suitesCount || 0 }})
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <!-- Modal: Gestión de Habitaciones de la Propiedad -->
          <div *ngIf="suiteModalOpen() && selectedPropertyForSuites" class="fixed inset-0 bg-black/85 z-[1000] flex items-center justify-center p-4">
            <div class="bg-[#111] border border-[#c9a96e] rounded-xl max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden shadow-2xl">
              <!-- Modal Header -->
              <div class="p-6 border-b border-[rgba(201,169,110,0.3)] flex justify-between items-center bg-[rgba(201,169,110,0.05)]">
                <div>
                  <h3 class="font-serif text-2xl text-[#c9a96e] mb-1">Habitaciones: {{ selectedPropertyForSuites.name }}</h3>
                  <p class="text-xs text-[#f5f0e8] opacity-70">📍 {{ selectedPropertyForSuites.city }} &bull; {{ selectedPropertyForSuites.address }}</p>
                </div>
                <button (click)="closeSuitesModal()" class="text-2xl text-[#f5f0e8] opacity-70 hover:opacity-100 px-3 py-1 cursor-pointer">&times;</button>
              </div>

              <!-- Modal Body -->
              <div class="p-6 overflow-y-auto space-y-6">
                <!-- Toast inside modal -->
                <div *ngIf="suiteSuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ suiteSuccess }}
                </div>
                <div *ngIf="suiteError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ suiteError }}
                </div>

                <!-- Create Room Form -->
                <div class="p-4 bg-[rgba(255,255,255,0.02)] border border-[rgba(201,169,110,0.2)] rounded-lg">
                  <h4 class="text-sm font-semibold text-[#c9a96e] uppercase tracking-wider mb-3">➕ Añadir Nueva Habitación</h4>
                  <form (ngSubmit)="submitSuite()" class="space-y-3">
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                      <div>
                        <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Nombre de la Habitación *</label>
                        <input type="text" [(ngModel)]="newSuite.name" name="suiteName" required placeholder="Ej: Suite Vista al Mar" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                      </div>
                      <div>
                        <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Tipo *</label>
                        <select [(ngModel)]="newSuite.type" name="suiteType" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                          <option value="Habitación">Habitación Estándar</option>
                          <option value="Suite Deluxe">Suite Deluxe</option>
                          <option value="Suite Imperial">Suite Imperial</option>
                          <option value="Villa">Villa Privada</option>
                          <option value="Bungalow">Bungalow</option>
                        </select>
                      </div>
                    </div>

                    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
                      <div>
                        <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Precio por Noche (COP) *</label>
                        <input type="number" [(ngModel)]="newSuite.basePrice" name="suitePrice" required min="50000" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                      </div>
                      <div>
                        <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Máx Huéspedes *</label>
                        <input type="number" [(ngModel)]="newSuite.maxGuests" name="suiteGuests" required min="1" max="10" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                      </div>
                      <div>
                        <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">Tamaño (m²)</label>
                        <input type="number" [(ngModel)]="newSuite.size" name="suiteSize" min="10" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                      </div>
                    </div>

                    <div>
                      <label class="block text-[10px] uppercase tracking-widest text-[#c9a96e] mb-1">URL Imagen de la Habitación</label>
                      <input type="url" [(ngModel)]="newSuite.mainImage" name="suiteImg" placeholder="https://images.unsplash.com/..." class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded px-3 py-1.5 text-xs text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    </div>

                    <div class="flex justify-end pt-2">
                      <button type="submit" [disabled]="submittingSuite" class="px-5 py-2 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded uppercase tracking-wider text-xs transition duration-200 cursor-pointer">
                        {{ submittingSuite ? 'Guardando...' : '➕ Guardar Habitación' }}
                      </button>
                    </div>
                  </form>
                </div>

                <!-- Existing Rooms List -->
                <div>
                  <h4 class="text-sm font-semibold text-[#c9a96e] uppercase tracking-wider mb-3">Habitaciones Registradas ({{ propertySuites().length }})</h4>
                  <div *ngIf="loadingSuites()" class="py-6 text-center text-xs text-[#f5f0e8] opacity-60">Cargando habitaciones...</div>
                  <div *ngIf="!loadingSuites() && propertySuites().length === 0" class="py-6 text-center text-xs text-[#f5f0e8] opacity-60 bg-[rgba(255,255,255,0.02)] rounded border border-[rgba(255,255,255,0.05)]">
                    No hay habitaciones registradas aún para este alojamiento. Agrega la primera en el formulario superior.
                  </div>
                  <div *ngIf="!loadingSuites() && propertySuites().length > 0" class="space-y-2">
                    <div *ngFor="let s of propertySuites()" class="p-3 bg-[#161616] border border-[rgba(255,255,255,0.08)] rounded-lg flex justify-between items-center">
                      <div>
                        <p class="text-xs font-semibold text-[#f5f0e8]">{{ s.name }}</p>
                        <p class="text-[10px] text-[#f5f0e8] opacity-60">{{ s.type }} &bull; Máx {{ s.maxGuests }} huéspedes &bull; {{ s.size || 30 }} m²</p>
                      </div>
                      <div class="text-right">
                        <p class="text-xs font-bold text-[#c9a96e]">$ {{ s.basePrice | number:'1.0-0' }} <span class="text-[10px] font-normal text-white/50">/ noche</span></p>
                        <span class="text-[9px] uppercase tracking-wider px-2 py-0.5 rounded-full bg-[#2ecc71]/20 text-[#2ecc71]">● Activa</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="p-4 border-t border-[rgba(201,169,110,0.2)] bg-[#0d0d0d] flex justify-end">
                <button type="button" (click)="closeSuitesModal()" class="px-5 py-2 bg-[rgba(255,255,255,0.1)] hover:bg-[rgba(255,255,255,0.2)] text-white rounded text-xs uppercase tracking-wider cursor-pointer">
                  Cerrar
                </button>
              </div>
            </div>
          </div>
        </ng-container>

        <!-- ==================== TAB 4: WEBHOOKS ==================== -->
        <ng-container *ngIf="activeTab() === 'webhooks'">
          <!-- Top Grid: Register Subscription + Active Subscriptions -->
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8 mb-8">
            <!-- Left: Register Webhook Form -->
            <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 shadow-xl">
              <h2 class="font-serif text-2xl text-[#c9a96e] mb-2">Canal de Notificaciones de Reservas</h2>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Recibe avisos inmediatos en tu sistema o página web cada vez que un huésped reserve, confirme su pago o cancele.</p>

              <form (ngSubmit)="submitWebhook()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Alojamiento a Conectar *</label>
                  <select [(ngModel)]="newWebhook.propertyId" name="whPropId" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option value="">-- Selecciona un Alojamiento --</option>
                    <option *ngFor="let prop of properties()" [value]="prop.id || prop._id">
                      {{ prop.name }} ({{ prop.city }})
                    </option>
                  </select>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Dirección de tu Sistema o Web (URL de recepción) *</label>
                  <input type="url" [(ngModel)]="newWebhook.url" name="whUrl" placeholder="https://mi-sistema-hotel.com/reservas-notificaciones" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-2">Eventos Notificados</label>
                  <div class="space-y-2 bg-[rgba(255,255,255,0.02)] p-3 rounded border border-[rgba(255,255,255,0.08)] text-xs">
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventCreated" name="evCreated" class="accent-[#c9a96e]">
                      <span>Nueva reserva iniciada (El huésped comenzó el proceso)</span>
                    </label>
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventConfirmed" name="evConfirmed" class="accent-[#c9a96e]">
                      <span>Reserva confirmada (Pago aprobado con éxito)</span>
                    </label>
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventCancelled" name="evCancelled" class="accent-[#c9a96e]">
                      <span>Reserva cancelada (Fechas liberadas para la venta)</span>
                    </label>
                  </div>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Nombre de la Conexión o Notas</label>
                  <input type="text" [(ngModel)]="newWebhook.description" name="whDesc" placeholder="Ej: Sincronización con mi sistema de recepción" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div *ngIf="webhookError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ webhookError }}
                </div>
                <div *ngIf="webhookSuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ webhookSuccess }}
                </div>

                <button type="submit" [disabled]="submittingWebhook" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingWebhook ? 'Guardando...' : 'Activar Canal de Notificaciones' }}
                </button>

                <!-- Demo helper shortcut -->
                <button type="button" (click)="useDemoReceiver()" class="w-full py-2 bg-transparent border border-[rgba(201,169,110,0.4)] text-[#c9a96e] hover:bg-[rgba(201,169,110,0.1)] rounded-md uppercase tracking-wider text-[11px] transition duration-200">
                  ⚡ Probar con Simulador de Recepción
                </button>
              </form>
            </div>

            <!-- Right: Subscriptions List -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h2 class="font-serif text-2xl text-[#c9a96e]">Canales de Notificación Activos ({{ subscriptions().length }})</h2>
                <button (click)="fetchSubscriptions(); fetchDeliveries();" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="subscriptions().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No tienes canales de notificación registrados. Conecta una dirección a la izquierda para empezar a recibir avisos automáticos de tus reservas.
              </div>

              <div class="space-y-4" *ngIf="subscriptions().length > 0">
                <div *ngFor="let sub of subscriptions()" class="bg-[#111] border border-[rgba(201,169,110,0.25)] rounded-xl p-5 transition flex flex-col justify-between">
                  <div>
                    <div class="flex justify-between items-start gap-4 mb-2">
                      <div>
                        <span class="font-mono text-sm text-[#2ecc71] font-semibold break-all">{{ sub.url }}</span>
                        <p class="text-xs text-[#c9a96e] mt-0.5">Propiedad ID: {{ sub.propertyId }} <span *ngIf="sub.description">&bull; {{ sub.description }}</span></p>
                      </div>
                      <span [ngClass]="sub.active ? 'bg-[rgba(46,204,113,0.2)] text-[#2ecc71]' : 'bg-[rgba(231,76,60,0.2)] text-[#e74c3c]'" class="px-2.5 py-0.5 font-bold text-[10px] tracking-wider uppercase rounded-full whitespace-nowrap">
                        {{ sub.active ? '● Activo' : '○ Inactivo' }}
                      </span>
                    </div>

                    <!-- Secret & Events tags -->
                    <div class="bg-[rgba(0,0,0,0.4)] border border-[rgba(255,255,255,0.06)] rounded p-2.5 mb-3 flex flex-wrap justify-between items-center text-xs">
                      <div class="flex items-center gap-2">
                        <span class="text-[#c9a96e] font-semibold uppercase text-[10px]">Secret:</span>
                        <code class="text-[#f5f0e8] font-mono select-all bg-[rgba(255,255,255,0.05)] px-2 py-0.5 rounded">{{ sub.secret }}</code>
                      </div>
                      <div class="flex gap-1 flex-wrap mt-1 md:mt-0">
                        <span *ngFor="let ev of sub.events" class="bg-[rgba(201,169,110,0.15)] text-[#c9a96e] text-[10px] font-mono px-2 py-0.5 rounded">
                          {{ ev }}
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- Actions -->
                  <div class="pt-3 border-t border-[rgba(255,255,255,0.08)] flex justify-end gap-3 text-xs">
                    <button (click)="testWebhook(sub.id)" class="px-3 py-1.5 bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.35)] text-[#c9a96e] rounded text-xs font-semibold uppercase tracking-wider transition">
                      🧪 Enviar Evento de Prueba
                    </button>
                    <button (click)="deleteWebhook(sub.id)" class="px-3 py-1.5 bg-[rgba(231,76,60,0.15)] hover:bg-[rgba(231,76,60,0.3)] text-[#e74c3c] rounded text-xs font-semibold uppercase tracking-wider transition">
                      Eliminar
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Bottom: Deliveries Log Table -->
          <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 shadow-xl mb-8">
            <div class="flex justify-between items-center mb-4">
              <div>
                <h2 class="font-serif text-2xl text-[#c9a96e]">Historial de Entregas & Reintentos</h2>
                <p class="text-xs text-[#f5f0e8] opacity-70">Monitoreo de entregas en tiempo real con políticas de reintento exponencial (1m / 5m / 30m).</p>
              </div>
              <button (click)="fetchDeliveries()" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Actualizar Logs</button>
            </div>

            <div *ngIf="deliveries().length === 0" class="p-8 text-center text-[#f5f0e8] opacity-60 text-sm">
              No hay registros de entregas para tus propiedades aún.
            </div>

            <div class="overflow-x-auto" *ngIf="deliveries().length > 0">
              <table class="w-full text-left text-xs">
                <thead>
                  <tr class="border-b border-[rgba(255,255,255,0.1)] text-[#c9a96e] uppercase tracking-wider text-[10px]">
                    <th class="pb-3">ID Evento</th>
                    <th class="pb-3">Tipo de Aviso</th>
                    <th class="pb-3">Destino</th>
                    <th class="pb-3 text-center">Estado</th>
                    <th class="pb-3 text-center">Intentos</th>
                    <th class="pb-3 text-center">Código HTTP</th>
                    <th class="pb-3">Fecha</th>
                    <th class="pb-3 text-right">Acción</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-[rgba(255,255,255,0.05)] text-[#f5f0e8]">
                  <tr *ngFor="let del of deliveries()" class="hover:bg-[rgba(255,255,255,0.02)] transition">
                    <td class="py-3 font-mono text-[11px] text-[#c9a96e]">{{ del.eventId }}</td>
                    <td class="py-3 font-mono font-semibold">{{ del.eventType }}</td>
                    <td class="py-3 truncate max-w-xs text-[#f5f0e8] opacity-80" [title]="del.targetUrl">{{ del.targetUrl }}</td>
                    <td class="py-3 text-center">
                      <span [ngClass]="del.status === 'SUCCESS' ? 'bg-[rgba(46,204,113,0.2)] text-[#2ecc71]' : (del.status === 'PENDING' ? 'bg-[rgba(241,196,15,0.2)] text-[#f1c40f]' : 'bg-[rgba(231,76,60,0.2)] text-[#e74c3c]')" class="px-2 py-0.5 font-bold text-[9px] tracking-wider uppercase rounded-full">
                        {{ del.status === 'SUCCESS' ? '✔ Exitoso' : (del.status === 'PENDING' ? '⏳ Reintentando' : '✖ Fallido') }}
                      </span>
                    </td>
                    <td class="py-3 text-center font-mono">{{ del.attempts }} / {{ del.maxAttempts }}</td>
                    <td class="py-3 text-center font-mono" [ngClass]="del.responseCode && del.responseCode >= 200 && del.responseCode < 300 ? 'text-[#2ecc71]' : 'text-[#e74c3c]'">
                      {{ del.responseCode || 'N/A' }}
                    </td>
                    <td class="py-3 text-[#f5f0e8] opacity-60">{{ del.createdAt | date:'short' }}</td>
                    <td class="py-3 text-right">
                      <button (click)="redeliver(del.id)" class="px-2.5 py-1 bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] rounded text-[10px] uppercase tracking-wider font-semibold transition">
                        Reintentar Envío
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- Receptor Demo Live Box -->
          <div class="bg-gradient-to-r from-[#111] via-[#1a1612] to-[#111] border border-[rgba(201,169,110,0.4)] rounded-xl p-6 shadow-2xl">
            <div class="flex flex-wrap justify-between items-center mb-4 gap-4">
              <div>
                <h3 class="font-serif text-xl text-[#c9a96e] flex items-center gap-2">
                  <span>📡 Simulador de Recepción Externa en Tiempo Real</span>
                  <span class="text-xs bg-[rgba(201,169,110,0.2)] px-2 py-0.5 rounded text-[#c9a96e] font-sans font-normal">Demostración en vivo</span>
                </h3>
                <p class="text-xs text-[#f5f0e8] opacity-75 mt-1">Este simulador emula la recepción de un hotel o sistema externo para comprobar la llegada de alertas automáticas y la tolerancia a fallos.</p>
              </div>
              <div class="flex items-center gap-3">
                <button (click)="toggleDemoFailure()" [ngClass]="demoSimulateFailure() ? 'bg-[#e74c3c] text-white' : 'bg-[rgba(255,255,255,0.08)] text-[#f5f0e8] hover:bg-[rgba(255,255,255,0.15)]'" class="px-4 py-2 rounded-md text-xs font-semibold uppercase tracking-wider transition border border-[rgba(255,255,255,0.2)]">
                  {{ demoSimulateFailure() ? '🔴 Simulación de Fallo Activa (Prueba de Reintentos)' : '🟢 Sistema Receptor Saludable (HTTP 200)' }}
                </button>
                <button (click)="fetchDemoEvents()" class="px-3 py-2 bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] rounded text-xs font-semibold uppercase tracking-wider transition">
                  Actualizar Bandeja de Alertas
                </button>
              </div>
            </div>

            <div *ngIf="demoEvents().length === 0" class="p-6 text-center text-[#f5f0e8] opacity-50 text-xs border border-dashed border-[rgba(201,169,110,0.2)] rounded-lg">
              El simulador está a la espera de avisos. Activa un canal apuntando a este simulador y confirma una reserva para ver el aviso reflejado aquí.
            </div>

            <div class="space-y-3" *ngIf="demoEvents().length > 0">
              <div *ngFor="let ev of demoEvents()" class="bg-[#0a0a0a] border border-[rgba(201,169,110,0.2)] rounded-lg p-4 text-xs font-mono">
                <div class="flex justify-between items-center mb-2">
                  <span class="text-[#2ecc71] font-bold">{{ ev.eventType }}</span>
                  <span class="text-[#f5f0e8] opacity-50">{{ ev.receivedAt | date:'mediumTime' }}</span>
                </div>
                <div class="text-[#f5f0e8] opacity-75 break-all text-[11px] mb-2">
                  <span class="text-[#c9a96e]">X-Palacio-Signature:</span> {{ ev.signature }}
                </div>
                <pre class="bg-[#141414] p-2.5 rounded text-[10px] text-[#f5f0e8] opacity-90 overflow-x-auto">{{ ev.payload }}</pre>
              </div>
            </div>
          </div>
        </ng-container>

        <!-- ==================== TAB 5: CONEXIÓN PMS / CANAL EXTERNO ==================== -->
        <ng-container *ngIf="activeTab() === 'apikeys'">
          <!-- Top info banner with demo external hotel link -->
          <div class="bg-gradient-to-r from-[#111] via-[#1c1813] to-[#111] border border-[rgba(201,169,110,0.4)] rounded-xl p-6 shadow-xl mb-8 flex flex-wrap justify-between items-center gap-4">
            <div>
              <span class="text-[10px] font-mono uppercase tracking-widest text-[#2ecc71] bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] px-2.5 py-0.5 rounded">Sincronización Hotelera Bidireccional</span>
              <h2 class="font-serif text-2xl text-[#c9a96e] mt-2">Conexión con Software Hotelero y Channel Managers</h2>
              <p class="text-xs text-[#f5f0e8] opacity-75 mt-1 max-w-2xl">
                Conecta tu sistema de administración hotelera (PMS como Cloudbeds, Sirvoy o tu web propia) para publicar habitaciones y bloquear fechas automáticamente, evitando cualquier sobreventa en tus alojamientos.
              </p>
            </div>
            <a routerLink="/demo/external-hotel" target="_blank" class="px-5 py-2.5 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-lg text-xs uppercase tracking-wider transition flex items-center gap-2 shadow-lg">
              <span>🏨 Abrir Simulador de Hotel Aliado (PMS)</span>
              <span>&rarr;</span>
            </a>
          </div>

          <!-- Newly Created Key Alert Box -->
          <div *ngIf="recentlyCreatedKey()" class="mb-8 p-5 bg-[rgba(201,169,110,0.1)] border-2 border-[#c9a96e] rounded-xl shadow-2xl">
            <div class="flex justify-between items-start gap-4">
              <div class="flex-1">
                <p class="text-xs font-bold uppercase tracking-wider text-[#c9a96e] flex items-center gap-2">
                  <span>⚠️ ¡Guarda tu clave de conexión ahora!</span>
                </p>
                <p class="text-xs text-[#f5f0e8] opacity-80 mt-1">
                  Por seguridad de tu alojamiento, esta es la <strong>única vez</strong> que se mostrará la clave completa. Cópiala y configúrala en tu sistema hotelero:
                </p>
                <div class="mt-3 flex items-center gap-3">
                  <input type="text" readonly [value]="recentlyCreatedKey()" class="bg-[#000] border border-[rgba(201,169,110,0.5)] text-[#2ecc71] font-mono text-sm px-4 py-2 rounded-md w-full max-w-lg select-all">
                  <button (click)="copyRecentlyCreatedKey()" class="px-4 py-2 bg-[rgba(201,169,110,0.3)] hover:bg-[#c9a96e] hover:text-[#0d0d0d] text-[#c9a96e] rounded-md text-xs font-bold uppercase tracking-wider transition">
                    Copiar
                  </button>
                </div>
              </div>
              <button (click)="recentlyCreatedKey.set('')" class="text-[#f5f0e8] opacity-60 hover:opacity-100 text-sm">✕</button>
            </div>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <!-- Left: Create API Key Form -->
            <div class="bg-[#111] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 shadow-xl">
              <h3 class="font-serif text-xl text-[#c9a96e] mb-2">Generar Clave de Sincronización</h3>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Crea una credencial cifrada exclusiva para autorizar a tu software hotelero a sincronizar calendarios y habitaciones.</p>

              <form (ngSubmit)="submitApiKey()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Alojamiento a Vincular *</label>
                  <select [(ngModel)]="newApiKey.propertyId" name="keyPropId" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option value="">-- Selecciona un Alojamiento --</option>
                    <option *ngFor="let prop of properties()" [value]="prop.id || prop._id">
                      {{ prop.name }} ({{ prop.city }})
                    </option>
                  </select>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Nombre del Sistema o Uso *</label>
                  <input type="text" [(ngModel)]="newApiKey.name" name="keyName" placeholder="Ej: Conexión PMS Recepción Principal" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div *ngIf="apiKeyError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ apiKeyError }}
                </div>
                <div *ngIf="apiKeySuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ apiKeySuccess }}
                </div>

                <button type="submit" [disabled]="submittingApiKey" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingApiKey ? 'Generando...' : 'Generar Clave de Conexión' }}
                </button>
              </form>
            </div>

            <!-- Right: Active API Keys List -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h3 class="font-serif text-2xl text-[#c9a96e]">Claves de Conexión Activas ({{ apiKeys().length }})</h3>
                <button (click)="fetchApiKeys()" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="apiKeys().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No tienes claves de sincronización creadas todavía. Genera una en el formulario para vincular tu software hotelero o channel manager.
              </div>

              <div class="space-y-4" *ngIf="apiKeys().length > 0">
                <div *ngFor="let k of apiKeys()" class="bg-[#111] border border-[rgba(201,169,110,0.25)] rounded-xl p-5 flex flex-col justify-between">
                  <div>
                    <div class="flex justify-between items-start gap-4 mb-2">
                      <div>
                        <h4 class="font-semibold text-base text-[#f5f0e8]">{{ k.name }}</h4>
                        <p class="text-xs text-[#c9a96e] mt-0.5">Propiedad ID: {{ k.propertyId }}</p>
                      </div>
                      <span [ngClass]="k.active ? 'bg-[rgba(46,204,113,0.2)] text-[#2ecc71]' : 'bg-[rgba(231,76,60,0.2)] text-[#e74c3c]'" class="px-2.5 py-0.5 font-bold text-[10px] tracking-wider uppercase rounded-full whitespace-nowrap">
                        {{ k.active ? '● Activa' : '○ Revocada' }}
                      </span>
                    </div>

                    <div class="bg-[rgba(0,0,0,0.4)] border border-[rgba(255,255,255,0.06)] rounded p-2.5 mb-3 flex flex-wrap justify-between items-center text-xs">
                      <div class="flex items-center gap-2">
                        <span class="text-[#c9a96e] font-semibold uppercase text-[10px]">Prefijo:</span>
                        <code class="text-[#2ecc71] font-mono bg-[rgba(255,255,255,0.05)] px-2 py-0.5 rounded">{{ k.keyPrefix }}</code>
                      </div>
                      <div class="text-[10px] text-[#f5f0e8] opacity-60 mt-1 md:mt-0">
                        Último uso: {{ k.lastUsedAt ? (k.lastUsedAt | date:'short') : 'Nunca' }}
                      </div>
                    </div>
                  </div>

                  <div class="pt-3 border-t border-[rgba(255,255,255,0.08)] flex justify-end gap-3 text-xs">
                    <button *ngIf="k.active" (click)="revokeApiKey(k.id)" class="px-3 py-1.5 bg-[rgba(231,76,60,0.15)] hover:bg-[rgba(231,76,60,0.3)] text-[#e74c3c] rounded text-xs font-semibold uppercase tracking-wider transition">
                      Revocar Clave
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </ng-container>

        <!-- ==================== TAB 6: LIQUIDACIONES Y DESEMBOLSOS (92%) ==================== -->
        <ng-container *ngIf="activeTab() === 'payouts'">
          <div class="space-y-6">
            <!-- Header explicativo y botón de acción -->
            <div class="bg-[#141414] border border-[rgba(201,169,110,0.3)] rounded-xl p-6 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
              <div>
                <h3 class="text-xl font-bold text-[#c9a96e] mb-1">Cortes y Liquidaciones Automáticas a Anfitriones</h3>
                <p class="text-xs text-[#f5f0e8] opacity-75 max-w-2xl">
                  Proceso desatendido por lotes: El sistema agrupa reservas finalizadas, descuenta automáticamente la comisión del 
                  <strong class="text-[#c9a96e]">8%</strong> de la plataforma y genera el saldo neto a transferir (<strong class="text-[#2ecc71]">92%</strong>). 
                  Cada liquidación emite el evento y webhook <code class="text-[#2ecc71] bg-black/40 px-1 py-0.5 rounded">payout.processed</code>.
                </p>
              </div>
              <button (click)="triggerImmediatePayout()" class="px-5 py-2.5 bg-[#c9a96e] hover:bg-[#b5955a] text-[#0d0d0d] font-bold text-xs uppercase tracking-wider rounded-lg transition shadow-lg flex items-center gap-2 whitespace-nowrap cursor-pointer">
                <span>⚡</span> Ejecutar Corte de Liquidación Ahora
              </button>
            </div>

            <!-- Listado de Liquidaciones -->
            <div class="bg-[#141414] border border-[rgba(255,255,255,0.08)] rounded-xl overflow-hidden">
              <div class="p-5 border-b border-[rgba(255,255,255,0.08)] flex justify-between items-center">
                <h4 class="text-sm font-semibold uppercase tracking-wider text-[#f5f0e8]">Historial de Liquidaciones Generadas ({{ payouts().length }})</h4>
                <button (click)="fetchPayouts()" class="text-xs text-[#c9a96e] hover:underline flex items-center gap-1">
                  <span>🔄</span> Actualizar
                </button>
              </div>

              <div *ngIf="payouts().length === 0" class="p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No hay liquidaciones generadas todavía. Las reservas con check-in o check-out completado se liquidan automáticamente al procesar el corte.
              </div>

              <div *ngIf="payouts().length > 0" class="overflow-x-auto">
                <table class="w-full text-left text-xs text-[#f5f0e8]">
                  <thead class="bg-[#0d0d0d] text-[10px] uppercase tracking-wider text-[#c9a96e] border-b border-[rgba(255,255,255,0.08)]">
                    <tr>
                      <th class="p-4">Código</th>
                      <th class="p-4">Alojamiento</th>
                      <th class="p-4">Período</th>
                      <th class="p-4 text-center">Reservas</th>
                      <th class="p-4 text-right">Total Bruto</th>
                      <th class="p-4 text-right">Comisión Plataforma (8%)</th>
                      <th class="p-4 text-right font-bold text-[#2ecc71]">Neto a Desembolsar (92%)</th>
                      <th class="p-4 text-center">Estado</th>
                      <th class="p-4">Fecha Proceso</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-[rgba(255,255,255,0.05)]">
                    <tr *ngFor="let p of payouts()" class="hover:bg-[rgba(201,169,110,0.04)] transition">
                      <td class="p-4 font-mono font-bold text-[#c9a96e]">{{ p.payoutCode }}</td>
                      <td class="p-4 font-semibold">{{ p.propertyName || p.propertyId }}</td>
                      <td class="p-4 text-xs opacity-75">{{ p.periodStart }} — {{ p.periodEnd }}</td>
                      <td class="p-4 text-center font-bold">{{ p.totalBookings }}</td>
                      <td class="p-4 text-right">$ {{ (p.grossTotal || 0) | number:'1.0-0' }}</td>
                      <td class="p-4 text-right text-[#e74c3c]">-$ {{ (p.platformFee || 0) | number:'1.0-0' }}</td>
                      <td class="p-4 text-right font-bold text-[#2ecc71] text-sm">$ {{ (p.netAmount || 0) | number:'1.0-0' }}</td>
                      <td class="p-4 text-center">
                        <span class="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[rgba(46,204,113,0.15)] text-[#2ecc71] border border-[rgba(46,204,113,0.3)]">
                          {{ p.status }}
                        </span>
                      </td>
                      <td class="p-4 text-xs opacity-60">{{ p.processedAt | date:'short' }}</td>
                    </tr>
                  </tbody>
                </table>
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
  
  activeTab = signal<'bookings' | 'promotions' | 'properties' | 'webhooks' | 'apikeys' | 'payouts'>('bookings');

  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  promotions = signal<HostPromotionItem[]>([]);
  properties = signal<Property[]>([]);
  subscriptions = signal<WebhookSubscription[]>([]);
  deliveries = signal<WebhookDelivery[]>([]);
  demoEvents = signal<any[]>([]);
  demoSimulateFailure = signal<boolean>(false);
  payouts = signal<any[]>([]);
  loading = signal(true);
  actionMessage = signal<string>('');
  actionError = signal<string>('');

  fetchPayouts() {
    this.api.get<ApiResponse<any[]>>('/host/properties/payouts').subscribe({
      next: (res) => {
        const data = res?.data || (res as any) || [];
        this.payouts.set(Array.isArray(data) ? data : []);
      },
      error: () => this.payouts.set([])
    });
  }

  triggerImmediatePayout() {
    this.api.post<ApiResponse<any[]>>('/host/properties/payouts/process-now', {}).subscribe({
      next: (res) => {
        this.actionMessage.set('¡Liquidación ejecutada exitosamente! Se procesaron los lotes pendientes.');
        this.fetchPayouts();
      },
      error: (err) => {
        this.actionError.set(err.error?.message || 'Error al ejecutar liquidación');
      }
    });
  }

  // Pagination & Filters state
  currentPage = signal<number>(1);
  limit = signal<number>(10);
  totalPages = signal<number>(1);
  totalItems = signal<number>(0);

  searchQuery = signal<string>('');

  // Host Promotion Form
  newPromo = {
    title: '',
    branchId: '',
    startDate: '',
    endDate: '',
    discountPercent: 15,
    code: ''
  };
  submittingPromo = false;
  promoError = '';
  promoSuccess = '';

  // Host Property Form
  newProperty = {
    name: '',
    type: 'HOTEL',
    city: 'Cartagena',
    address: '',
    description: '',
    phone: '',
    mainImage: ''
  };
  submittingProperty = false;
  propertyError = '';
  propertySuccess = '';

  // Host Webhook Form
  newWebhook = {
    propertyId: '',
    url: '',
    description: '',
    eventCreated: true,
    eventConfirmed: true,
    eventCancelled: true
  };
  submittingWebhook = false;
  webhookError = '';
  webhookSuccess = '';

  // Host API Keys State
  apiKeys = signal<ApiKeyItem[]>([]);
  recentlyCreatedKey = signal<string>('');
  newApiKey = {
    propertyId: '',
    name: ''
  };
  submittingApiKey = false;
  apiKeyError = '';
  apiKeySuccess = '';

  // Suite Management for Property State
  selectedPropertyForSuites: Property | null = null;
  propertySuites = signal<any[]>([]);
  loadingSuites = signal<boolean>(false);
  suiteModalOpen = signal<boolean>(false);
  submittingSuite = false;
  suiteError = '';
  suiteSuccess = '';

  newSuite = {
    name: '',
    type: 'Habitación',
    basePrice: 250000,
    maxGuests: 2,
    size: 28,
    description: '',
    mainImage: ''
  };

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
    this.fetchPromotions();
    this.fetchProperties();
    this.fetchSubscriptions();
    this.fetchDeliveries();
    this.fetchDemoFailureStatus();
    this.fetchApiKeys();
  }

  fetchApiKeys() {
    this.api.get<ApiResponse<ApiKeyItem[]>>('/host/api-keys').subscribe({
      next: (res) => {
        const list = res?.data || (res as any) || [];
        this.apiKeys.set(Array.isArray(list) ? list : []);
      }
    });
  }

  submitApiKey() {
    this.apiKeyError = '';
    this.apiKeySuccess = '';

    if (!this.newApiKey.propertyId || !this.newApiKey.name) {
      this.apiKeyError = 'Por favor selecciona la propiedad e indica un nombre identificador';
      return;
    }

    this.submittingApiKey = true;
    this.api.post<ApiResponse<ApiKeyItem>>('/host/api-keys', this.newApiKey).subscribe({
      next: (res) => {
        this.submittingApiKey = false;
        this.apiKeySuccess = '¡API Key generada exitosamente!';
        if (res?.data?.rawKey) {
          this.recentlyCreatedKey.set(res.data.rawKey);
        }
        this.newApiKey = { propertyId: '', name: '' };
        this.fetchApiKeys();
      },
      error: (err) => {
        this.submittingApiKey = false;
        this.apiKeyError = err?.error?.message || 'Error al generar la API Key';
      }
    });
  }

  revokeApiKey(id: string) {
    if (!id || !confirm('¿Estás seguro de revocar esta API Key? Los sistemas externos que la utilicen dejarán de tener acceso.')) return;
    this.api.delete<ApiResponse<void>>(`/host/api-keys/${id}`).subscribe({
      next: () => {
        this.actionMessage.set('API Key revocada.');
        setTimeout(() => this.actionMessage.set(''), 3000);
        this.fetchApiKeys();
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al revocar API Key');
        setTimeout(() => this.actionError.set(''), 4000);
      }
    });
  }

  copyRecentlyCreatedKey() {
    if (this.recentlyCreatedKey()) {
      navigator.clipboard.writeText(this.recentlyCreatedKey());
      this.actionMessage.set('Clave copiada al portapapeles');
      setTimeout(() => this.actionMessage.set(''), 3000);
    }
  }

  fetchSubscriptions() {
    this.api.get<ApiResponse<WebhookSubscription[]>>('/host/webhooks').subscribe({
      next: (res) => {
        const list = res?.data || (res as any) || [];
        this.subscriptions.set(Array.isArray(list) ? list : []);
      }
    });
  }

  fetchDeliveries() {
    this.api.get<ApiResponse<WebhookDelivery[]>>('/host/webhooks/deliveries').subscribe({
      next: (res) => {
        const list = res?.data || (res as any) || [];
        this.deliveries.set(Array.isArray(list) ? list : []);
      }
    });
  }

  useDemoReceiver() {
    const defaultUrl = 'http://localhost:5000/api/demo/webhook-receiver';
    this.newWebhook.url = defaultUrl;
    if (this.properties().length > 0 && !this.newWebhook.propertyId) {
      this.newWebhook.propertyId = this.properties()[0].id || (this.properties()[0] as any)._id || '';
    }
    this.newWebhook.description = 'Receptor Demo Local para sustentación';
  }

  submitWebhook() {
    this.webhookError = '';
    this.webhookSuccess = '';

    if (!this.newWebhook.propertyId || !this.newWebhook.url) {
      this.webhookError = 'Por favor selecciona la propiedad e indica la URL de destino';
      return;
    }

    const events: string[] = [];
    if (this.newWebhook.eventCreated) events.push('booking.created');
    if (this.newWebhook.eventConfirmed) events.push('booking.confirmed');
    if (this.newWebhook.eventCancelled) events.push('booking.cancelled');

    if (events.length === 0) {
      this.webhookError = 'Debes suscribirte al menos a un evento';
      return;
    }

    this.submittingWebhook = true;
    const body = {
      propertyId: this.newWebhook.propertyId,
      url: this.newWebhook.url,
      events: events,
      description: this.newWebhook.description
    };

    this.api.post<ApiResponse<WebhookSubscription>>('/host/webhooks', body).subscribe({
      next: () => {
        this.submittingWebhook = false;
        this.webhookSuccess = '¡Webhook registrado exitosamente! Secret generado de forma segura.';
        this.newWebhook = {
          propertyId: '',
          url: '',
          description: '',
          eventCreated: true,
          eventConfirmed: true,
          eventCancelled: true
        };
        this.fetchSubscriptions();
      },
      error: (err) => {
        this.submittingWebhook = false;
        this.webhookError = err?.error?.message || 'Error al registrar el webhook';
      }
    });
  }

  deleteWebhook(id: string) {
    if (!id || !confirm('¿Estás seguro de eliminar este endpoint de webhook?')) return;
    this.api.delete<ApiResponse<void>>(`/host/webhooks/${id}`).subscribe({
      next: () => {
        this.actionMessage.set('Webhook eliminado.');
        setTimeout(() => this.actionMessage.set(''), 3000);
        this.fetchSubscriptions();
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al eliminar webhook');
        setTimeout(() => this.actionError.set(''), 4000);
      }
    });
  }

  testWebhook(subscriptionId: string) {
    this.actionMessage.set('Enviando evento de prueba...');
    this.api.post<ApiResponse<WebhookDelivery>>(`/host/webhooks/${subscriptionId}/test`, {}).subscribe({
      next: (res) => {
        this.actionMessage.set(`Evento de prueba enviado (HTTP ${res?.data?.responseCode || 200})`);
        setTimeout(() => this.actionMessage.set(''), 4000);
        this.fetchDeliveries();
        this.fetchDemoEvents();
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al enviar evento de prueba');
        setTimeout(() => this.actionError.set(''), 5000);
        this.fetchDeliveries();
      }
    });
  }

  redeliver(deliveryId: string) {
    this.actionMessage.set('Reenviando webhook...');
    this.api.post<ApiResponse<WebhookDelivery>>(`/host/webhooks/deliveries/${deliveryId}/redeliver`, {}).subscribe({
      next: (res) => {
        this.actionMessage.set(`Webhook reenviado (HTTP ${res?.data?.responseCode || 200})`);
        setTimeout(() => this.actionMessage.set(''), 4000);
        this.fetchDeliveries();
        this.fetchDemoEvents();
      },
      error: (err) => {
        this.actionError.set(err?.error?.message || 'Error al reenviar webhook');
        setTimeout(() => this.actionError.set(''), 5000);
        this.fetchDeliveries();
      }
    });
  }

  fetchDemoFailureStatus() {
    this.api.get<ApiResponse<any>>('/demo/webhook-receiver/simulate-failure').subscribe({
      next: (res) => {
        const val = res?.data?.simulateFailure ?? false;
        this.demoSimulateFailure.set(val);
      }
    });
  }

  toggleDemoFailure() {
    const nextVal = !this.demoSimulateFailure();
    this.api.post<ApiResponse<any>>(`/demo/webhook-receiver/simulate-failure?enabled=${nextVal}`, {}).subscribe({
      next: (res) => {
        this.demoSimulateFailure.set(res?.data?.simulateFailure ?? nextVal);
      }
    });
  }

  fetchDemoEvents() {
    this.api.get<ApiResponse<any[]>>('/demo/webhook-receiver/events').subscribe({
      next: (res) => {
        const list = res?.data || (res as any) || [];
        this.demoEvents.set(Array.isArray(list) ? list : []);
      }
    });
  }

  fetchProperties() {
    this.api.get<ApiResponse<Property[]>>('/host/properties').subscribe({
      next: (res) => {
        const list = res.data || (res as any) || [];
        const propList = Array.isArray(list) ? list : [];
        this.properties.set(propList);
        if (propList.length > 0 && !this.newPromo.branchId) {
          this.newPromo.branchId = propList[0].id || (propList[0] as any)._id || '';
        }
      }
    });
  }

  submitProperty() {
    this.propertyError = '';
    this.propertySuccess = '';

    if (!this.newProperty.name || !this.newProperty.city || !this.newProperty.address) {
      this.propertyError = 'Por favor completa nombre, ciudad y dirección';
      return;
    }

    this.submittingProperty = true;
    this.api.post<ApiResponse<Property>>('/host/properties', this.newProperty).subscribe({
      next: (res) => {
        this.submittingProperty = false;
        this.propertySuccess = '¡Alojamiento registrado exitosamente! En revisión por administración.';
        this.newProperty = {
          name: '',
          type: 'HOTEL',
          city: 'Cartagena',
          address: '',
          description: '',
          phone: '',
          mainImage: ''
        };
        this.fetchProperties();
      },
      error: (err) => {
        this.submittingProperty = false;
        this.propertyError = err?.error?.message || 'Error al registrar el alojamiento';
      }
    });
  }

  openSuitesModal(prop: Property) {
    this.selectedPropertyForSuites = prop;
    this.suiteError = '';
    this.suiteSuccess = '';
    this.suiteModalOpen.set(true);
    const propId = prop.id || prop._id;
    if (propId) {
      this.fetchPropertySuites(propId);
    }
  }

  closeSuitesModal() {
    this.suiteModalOpen.set(false);
    this.selectedPropertyForSuites = null;
    this.propertySuites.set([]);
    this.suiteError = '';
    this.suiteSuccess = '';
  }

  fetchPropertySuites(propId: string) {
    this.loadingSuites.set(true);
    this.api.get<ApiResponse<any[]>>(`/host/properties/${propId}/suites`).subscribe({
      next: (res) => {
        this.loadingSuites.set(false);
        const list = res?.data || (res as any) || [];
        this.propertySuites.set(Array.isArray(list) ? list : []);
      },
      error: () => {
        this.loadingSuites.set(false);
        this.propertySuites.set([]);
      }
    });
  }

  submitSuite() {
    if (!this.selectedPropertyForSuites) return;
    this.suiteError = '';
    this.suiteSuccess = '';

    if (!this.newSuite.name || !this.newSuite.basePrice) {
      this.suiteError = 'Por favor ingresa nombre y precio de la habitación';
      return;
    }

    const propId = this.selectedPropertyForSuites.id || this.selectedPropertyForSuites._id;
    if (!propId) return;

    this.submittingSuite = true;
    this.api.post<ApiResponse<any>>(`/host/properties/${propId}/suites`, this.newSuite).subscribe({
      next: () => {
        this.submittingSuite = false;
        this.suiteSuccess = '¡Habitación agregada con éxito!';
        this.newSuite = {
          name: '',
          type: 'Habitación',
          basePrice: 250000,
          maxGuests: 2,
          size: 28,
          description: '',
          mainImage: ''
        };
        this.fetchPropertySuites(propId);
        this.fetchProperties();
      },
      error: (err) => {
        this.submittingSuite = false;
        this.suiteError = err?.error?.message || 'Error al guardar la habitación';
      }
    });
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

    if (!this.newPromo.branchId) {
      this.promoError = 'Debes seleccionar un alojamiento o sede para la oferta';
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
        const defaultScope = this.properties()[0]?.id || (this.properties()[0] as any)?._id || '';
        this.newPromo = {
          title: '',
          branchId: defaultScope,
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
