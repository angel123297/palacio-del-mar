import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Booking, ApiResponse, DashboardMetricsDTO, PaginatedResponse, Property, WebhookSubscription, WebhookDelivery } from '../../models/types';

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
              Webhooks
            </button>
            <button (click)="activeTab.set('promotions')" 
                    [ngClass]="activeTab() === 'promotions' ? 'bg-[#c9a96e] text-[#0d0d0d] font-bold' : 'text-[#f5f0e8] hover:text-[#c9a96e]'" 
                    class="px-5 py-2 rounded-md text-xs tracking-widest uppercase transition">
              Promociones
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
                      <span class="text-[#f5f0e8] opacity-60">{{ prop.suitesCount || 0 }} habitacion(es)</span>
                    </div>
                  </div>
                </div>
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
              <h2 class="font-serif text-2xl text-[#c9a96e] mb-2">Registrar Webhook Saliente</h2>
              <p class="text-xs text-[#f5f0e8] opacity-70 mb-6">Recibe eventos en tiempo real con firma criptográfica HMAC-SHA256 en tu propio servidor.</p>

              <form (ngSubmit)="submitWebhook()" class="space-y-4">
                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Alojamiento / Propiedad *</label>
                  <select [(ngModel)]="newWebhook.propertyId" name="whPropId" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                    <option value="">-- Selecciona un Alojamiento --</option>
                    <option *ngFor="let prop of properties()" [value]="prop.id || prop._id">
                      {{ prop.name }} ({{ prop.city }})
                    </option>
                  </select>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">URL de Destino (Endpoint HTTP/S) *</label>
                  <input type="url" [(ngModel)]="newWebhook.url" name="whUrl" placeholder="http://tu-servidor.com/webhook" required class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-2">Eventos Suscritos</label>
                  <div class="space-y-2 bg-[rgba(255,255,255,0.02)] p-3 rounded border border-[rgba(255,255,255,0.08)] text-xs">
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventCreated" name="evCreated" class="accent-[#c9a96e]">
                      <span>booking.created (Nueva reserva iniciada)</span>
                    </label>
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventConfirmed" name="evConfirmed" class="accent-[#c9a96e]">
                      <span>booking.confirmed (Pago aprobado y reserva confirmada)</span>
                    </label>
                    <label class="flex items-center gap-2 cursor-pointer">
                      <input type="checkbox" [(ngModel)]="newWebhook.eventCancelled" name="evCancelled" class="accent-[#c9a96e]">
                      <span>booking.cancelled (Reserva cancelada)</span>
                    </label>
                  </div>
                </div>

                <div>
                  <label class="block text-[11px] uppercase tracking-widest text-[#c9a96e] mb-1">Descripción / Notas</label>
                  <input type="text" [(ngModel)]="newWebhook.description" name="whDesc" placeholder="Ej: Integración con motor PMS propio" class="w-full bg-[#1a1a1a] border border-[rgba(255,255,255,0.15)] rounded-md px-3 py-2 text-sm text-[#f5f0e8] outline-none focus:border-[#c9a96e]">
                </div>

                <div *ngIf="webhookError" class="p-3 bg-[rgba(231,76,60,0.15)] border border-[#e74c3c] text-[#e74c3c] rounded text-xs">
                  {{ webhookError }}
                </div>
                <div *ngIf="webhookSuccess" class="p-3 bg-[rgba(46,204,113,0.15)] border border-[#2ecc71] text-[#2ecc71] rounded text-xs">
                  {{ webhookSuccess }}
                </div>

                <button type="submit" [disabled]="submittingWebhook" class="w-full py-3 bg-[#c9a96e] hover:bg-[#d8b87d] text-[#0d0d0d] font-bold rounded-md uppercase tracking-wider text-xs transition duration-200 mt-2">
                  {{ submittingWebhook ? 'Guardando...' : 'Crear Suscripción de Webhook' }}
                </button>

                <!-- Demo helper shortcut -->
                <button type="button" (click)="useDemoReceiver()" class="w-full py-2 bg-transparent border border-[rgba(201,169,110,0.4)] text-[#c9a96e] hover:bg-[rgba(201,169,110,0.1)] rounded-md uppercase tracking-wider text-[11px] transition duration-200">
                  ⚡ Auto-rellenar con Receptor Demo Local
                </button>
              </form>
            </div>

            <!-- Right: Subscriptions List -->
            <div class="lg:col-span-2 space-y-4">
              <div class="flex justify-between items-center mb-2">
                <h2 class="font-serif text-2xl text-[#c9a96e]">Endpoints Registrados ({{ subscriptions().length }})</h2>
                <button (click)="fetchSubscriptions(); fetchDeliveries();" class="text-xs text-[#c9a96e] hover:underline uppercase tracking-widest">Refrescar</button>
              </div>

              <div *ngIf="subscriptions().length === 0" class="border border-[rgba(201,169,110,0.2)] rounded-xl bg-[#111] p-12 text-center text-[#f5f0e8] opacity-60 text-sm">
                No tienes webhooks registrados. Agrega una URL a la izquierda para comenzar a recibir notificaciones automáticas.
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
                    <th class="pb-3">Event ID</th>
                    <th class="pb-3">Tipo</th>
                    <th class="pb-3">Destino</th>
                    <th class="pb-3 text-center">Estado</th>
                    <th class="pb-3 text-center">Intentos</th>
                    <th class="pb-3 text-center">HTTP</th>
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
                        Reenviar
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
                  <span>📡 Receptor Demo para la Sustentación</span>
                  <span class="text-xs bg-[rgba(201,169,110,0.2)] px-2 py-0.5 rounded text-[#c9a96e] font-sans font-normal">/api/demo/webhook-receiver</span>
                </h3>
                <p class="text-xs text-[#f5f0e8] opacity-75 mt-1">Este receptor emula el servidor de un anfitrión externo para verificar la firma HMAC-SHA256 y probar la tolerancia a fallos.</p>
              </div>
              <div class="flex items-center gap-3">
                <button (click)="toggleDemoFailure()" [ngClass]="demoSimulateFailure() ? 'bg-[#e74c3c] text-white' : 'bg-[rgba(255,255,255,0.08)] text-[#f5f0e8] hover:bg-[rgba(255,255,255,0.15)]'" class="px-4 py-2 rounded-md text-xs font-semibold uppercase tracking-wider transition border border-[rgba(255,255,255,0.2)]">
                  {{ demoSimulateFailure() ? '🔴 Fallo 500 Activo (Simulando Caída)' : '🟢 Receptor Saludable (HTTP 200)' }}
                </button>
                <button (click)="fetchDemoEvents()" class="px-3 py-2 bg-[rgba(201,169,110,0.2)] hover:bg-[rgba(201,169,110,0.4)] text-[#c9a96e] rounded text-xs font-semibold uppercase tracking-wider transition">
                  Refrescar Bandeja
                </button>
              </div>
            </div>

            <div *ngIf="demoEvents().length === 0" class="p-6 text-center text-[#f5f0e8] opacity-50 text-xs border border-dashed border-[rgba(201,169,110,0.2)] rounded-lg">
              El receptor demo está a la espera de eventos. Registra un webhook apuntando a este receptor y confirma una reserva para ver el evento firmado aquí.
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

      </div>
    </div>
  `
})
export class HostDashboardComponent implements OnInit {
  api = inject(ApiService);
  
  activeTab = signal<'bookings' | 'promotions' | 'properties' | 'webhooks'>('bookings');

  metrics = signal<DashboardMetricsDTO | null>(null);
  bookings = signal<Booking[]>([]);
  promotions = signal<HostPromotionItem[]>([]);
  properties = signal<Property[]>([]);
  subscriptions = signal<WebhookSubscription[]>([]);
  deliveries = signal<WebhookDelivery[]>([]);
  demoEvents = signal<any[]>([]);
  demoSimulateFailure = signal<boolean>(false);
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

  ngOnInit(): void {
    this.fetchMetrics();
    this.fetchBookings();
    this.fetchPromotions();
    this.fetchProperties();
    this.fetchSubscriptions();
    this.fetchDeliveries();
    this.fetchDemoFailureStatus();
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
        this.properties.set(Array.isArray(list) ? list : []);
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
