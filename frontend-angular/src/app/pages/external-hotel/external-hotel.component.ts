import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-external-hotel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="min-h-screen bg-[#0b0f17] text-[#e2e8f0] pt-24 pb-16 px-6">
      <div class="max-w-5xl mx-auto space-y-8">
        
        <!-- Header -->
        <header class="border-b border-slate-800 pb-6 flex flex-wrap justify-between items-center gap-4">
          <div>
            <span class="text-xs font-mono uppercase tracking-widest text-emerald-400 bg-emerald-950/60 border border-emerald-800/50 px-2.5 py-1 rounded">Simulador de Software Hotelero (PMS Aliado)</span>
            <h1 class="text-3xl font-bold text-white mt-2">Cartagena Luxury Beach Resort</h1>
            <p class="text-sm text-slate-400">Demostración de Sincronización Automática e Inventario en Tiempo Real con Palacio del Mar</p>
          </div>
          <a routerLink="/anfitrion" class="text-xs bg-amber-500/10 text-amber-300 border border-amber-500/30 px-4 py-2 rounded-lg hover:bg-amber-500/20 transition">
            &larr; Volver al Panel de Anfitrión
          </a>
        </header>

        <!-- Configuration Bar -->
        <div class="bg-slate-900 border border-slate-800 rounded-xl p-5 shadow-lg space-y-3">
          <h2 class="text-sm font-semibold text-slate-200 uppercase tracking-wider">Configuración de la Conexión</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1">Servidor Central de Palacio del Mar</label>
              <input [(ngModel)]="apiBase" type="text" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm font-mono text-emerald-400 focus:outline-none focus:border-emerald-500">
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1">Clave de Conexión del Hotel (Clave de Sincronización)</label>
              <input [(ngModel)]="apiKey" type="text" placeholder="pdm_live_..." class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-sm font-mono text-amber-300 focus:outline-none focus:border-amber-400">
            </div>
          </div>
          <p class="text-xs text-slate-500">Copia tu clave desde el Panel del Anfitrión (Pestaña "Conexión PMS / Integración").</p>
        </div>

        <!-- Main Actions Grid -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">

          <!-- Card 1: Upsert Room -->
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-col justify-between">
            <div>
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg font-bold text-white flex items-center gap-2">
                  <span>🏨 Sincronizar Tipo de Habitación</span>
                </h3>
              </div>
              <p class="text-xs text-slate-400 mb-4">Publica o actualiza una habitación desde tu sistema propio hacia el marketplace de Palacio del Mar.</p>

              <form (ngSubmit)="syncRoom()" class="space-y-3 text-xs">
                <div>
                  <label class="block text-slate-400 mb-1">Código de Habitación en tu Sistema (ID) *</label>
                  <input [(ngModel)]="room.externalId" name="rExtId" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                </div>
                <div>
                  <label class="block text-slate-400 mb-1">Nombre de la Habitación *</label>
                  <input [(ngModel)]="room.name" name="rName" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-slate-200">
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-slate-400 mb-1">Precio por Noche (COP) *</label>
                    <input [(ngModel)]="room.basePrice" name="rPrice" type="number" step="10000" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                  </div>
                  <div>
                    <label class="block text-slate-400 mb-1">Capacidad Huéspedes</label>
                    <input [(ngModel)]="room.maxGuests" name="rGuests" type="number" min="1" max="10" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                  </div>
                </div>
                <div>
                  <label class="block text-slate-400 mb-1">URL Imagen</label>
                  <input [(ngModel)]="room.mainImage" name="rImg" type="url" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-300 text-[11px]">
                </div>
                <button type="submit" [disabled]="loadingRoom" class="w-full py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded uppercase tracking-wider text-xs transition mt-2">
                  {{ loadingRoom ? 'Enviando...' : 'Publicar / Actualizar en Marketplace' }}
                </button>
              </form>
            </div>
            <div *ngIf="roomResult()" [ngClass]="roomSuccess() ? 'bg-emerald-950/60 border-emerald-800 text-emerald-300' : 'bg-rose-950/60 border-rose-800 text-rose-300'" class="mt-4 p-3 border rounded font-mono text-[11px] max-h-36 overflow-auto">
              {{ roomResult() }}
            </div>
          </div>

          <!-- Card 2: Availability & Anti-Overbooking -->
          <div class="bg-slate-900 border border-slate-800 rounded-xl p-6 shadow-xl flex flex-col justify-between">
            <div>
              <div class="flex items-center justify-between mb-4">
                <h3 class="text-lg font-bold text-white flex items-center gap-2">
                  <span>🔒 Bloqueo de Calendario (Protección Anti-Sobreventa)</span>
                </h3>
              </div>
              <p class="text-xs text-slate-400 mb-4">Cierra o abre fechas vendidas en tu sistema hotelero para evitar sobreventas en Palacio del Mar.</p>

              <form (ngSubmit)="syncAvailability()" class="space-y-3 text-xs">
                <div>
                  <label class="block text-slate-400 mb-1">Código de Habitación (ID) *</label>
                  <input [(ngModel)]="avail.externalId" name="aExtId" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-slate-400 mb-1">Check-in *</label>
                    <input [(ngModel)]="avail.startDate" name="aStart" type="date" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                  </div>
                  <div>
                    <label class="block text-slate-400 mb-1">Check-out *</label>
                    <input [(ngModel)]="avail.endDate" name="aEnd" type="date" required class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                  </div>
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-slate-400 mb-1">Acción</label>
                    <select [(ngModel)]="avail.action" name="aAction" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 font-mono text-slate-200">
                      <option value="BLOCK">🔒 BLOCK (Bloquear)</option>
                      <option value="UNBLOCK">🔓 UNBLOCK (Liberar)</option>
                    </select>
                  </div>
                  <div>
                    <label class="block text-slate-400 mb-1">Motivo / Canal</label>
                    <input [(ngModel)]="avail.reason" name="aReason" type="text" class="w-full bg-slate-950 border border-slate-700 rounded px-3 py-2 text-slate-200">
                  </div>
                </div>
                <button type="submit" [disabled]="loadingAvail" class="w-full py-2.5 bg-purple-600 hover:bg-purple-500 text-white font-semibold rounded uppercase tracking-wider text-xs transition mt-2">
                  {{ loadingAvail ? 'Sincronizando...' : 'Sincronizar Disponibilidad' }}
                </button>
              </form>
            </div>
            <div *ngIf="availResult()" [ngClass]="availSuccess() ? 'bg-purple-950/60 border-purple-800 text-purple-300' : 'bg-rose-950/60 border-rose-800 text-rose-300'" class="mt-4 p-3 border rounded font-mono text-[11px] max-h-36 overflow-auto">
              {{ availResult() }}
            </div>
          </div>

        </div>

      </div>
    </div>
  `
})
export class ExternalHotelComponent implements OnInit {
  http = inject(HttpClient);

  apiBase = 'http://localhost:5000/api';
  apiKey = '';

  room = {
    externalId: 'PMS-DELUXE-101',
    name: 'Suite Panorámica Mar & Coral',
    basePrice: 480000,
    maxGuests: 3,
    mainImage: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=800&q=80'
  };
  loadingRoom = false;
  roomResult = signal<string>('');
  roomSuccess = signal<boolean>(true);

  avail = {
    externalId: 'PMS-DELUXE-101',
    startDate: '',
    endDate: '',
    action: 'BLOCK',
    reason: 'Venta directa en mostrador hotel'
  };
  loadingAvail = false;
  availResult = signal<string>('');
  availSuccess = signal<boolean>(true);

  ngOnInit(): void {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const checkout = new Date(today);
    checkout.setDate(checkout.getDate() + 4);

    this.avail.startDate = tomorrow.toISOString().split('T')[0];
    this.avail.endDate = checkout.toISOString().split('T')[0];
  }

  syncRoom() {
    if (!this.apiKey) {
      alert('Por favor ingresa tu API Key en la barra superior.');
      return;
    }

    this.loadingRoom = true;
    this.roomResult.set('Enviando solicitud...');

    const headers = {
      'Content-Type': 'application/json',
      'X-Palacio-Api-Key': this.apiKey.trim()
    };

    const payload = {
      externalId: this.room.externalId,
      name: this.room.name,
      type: 'Habitación',
      basePrice: this.room.basePrice,
      maxGuests: this.room.maxGuests,
      mainImage: this.room.mainImage,
      amenities: ['Vista al mar', 'WiFi Alta Velocidad', 'Aire Acondicionado', 'Balcón Privado']
    };

    this.http.post<any>(`${this.apiBase}/integration/rooms`, payload, { headers }).subscribe({
      next: (res) => {
        this.loadingRoom = false;
        this.roomSuccess.set(true);
        this.roomResult.set('✔ Habitación Sincronizada: ' + JSON.stringify(res, null, 2));
      },
      error: (err) => {
        this.loadingRoom = false;
        this.roomSuccess.set(false);
        this.roomResult.set('✖ Error: ' + (err?.error?.message || err.message || JSON.stringify(err)));
      }
    });
  }

  syncAvailability() {
    if (!this.apiKey) {
      alert('Por favor ingresa tu API Key en la barra superior.');
      return;
    }

    this.loadingAvail = true;
    this.availResult.set('Sincronizando...');

    const headers = {
      'Content-Type': 'application/json',
      'X-Palacio-Api-Key': this.apiKey.trim()
    };

    this.http.put<any>(`${this.apiBase}/integration/availability`, this.avail, { headers }).subscribe({
      next: (res) => {
        this.loadingAvail = false;
        this.availSuccess.set(true);
        this.availResult.set('✔ Disponibilidad Actualizada: ' + JSON.stringify(res, null, 2));
      },
      error: (err) => {
        this.loadingAvail = false;
        this.availSuccess.set(false);
        this.availResult.set('✖ Error: ' + (err?.error?.message || err.message || JSON.stringify(err)));
      }
    });
  }
}
