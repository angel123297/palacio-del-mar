import { Component, AfterViewInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import * as L from 'leaflet';

@Component({
  selector: 'app-hotel-map',
  standalone: true,
  imports: [CommonModule],
  encapsulation: ViewEncapsulation.None,
  template: `
    <!-- Contenedor limpio sin ningún rastro de borde -->
    <div class="map-wrapper w-full max-w-7xl mx-auto px-4 mb-10 flex flex-col lg:flex-row gap-6 bg-[#0B0C10] p-6 rounded-2xl shadow-2xl relative z-10">
      
      <!-- Contenedor del Mapa -->
      <div class="w-full lg:w-2/3 h-[520px] rounded-xl overflow-hidden border border-[#C5A059]/60 relative shadow-inner">
        <div id="hotel-map" class="w-full h-full"></div>
        
        <!-- Selector de Capas (Aislado) -->
        <div class="absolute top-3 right-3 bg-[#121318]/95 border border-[#C5A059]/50 rounded-xl p-1.5 shadow-2xl z-[400] flex gap-1.5 backdrop-blur-md" 
             (click)="$event.stopPropagation()" (dblclick)="$event.stopPropagation()">
          <button type="button" (click)="changeLayer('streets'); $event.stopPropagation()" 
                  [class.bg-[#C5A059]]="currentLayer === 'streets'" 
                  [class.text-black]="currentLayer === 'streets'"
                  [class.text-gray-300]="currentLayer !== 'streets'"
                  class="px-3 py-1.5 rounded-lg text-xs font-serif font-semibold transition hover:bg-[#C5A059]/50">
            🗺️ Estándar
          </button>
          <button type="button" (click)="changeLayer('dark'); $event.stopPropagation()" 
                  [class.bg-[#C5A059]]="currentLayer === 'dark'" 
                  [class.text-black]="currentLayer === 'dark'"
                  [class.text-gray-300]="currentLayer !== 'dark'"
                  class="px-3 py-1.5 rounded-lg text-xs font-serif font-semibold transition hover:bg-[#C5A059]/50">
            🌙 Oscuro
          </button>
          <button type="button" (click)="changeLayer('satellite'); $event.stopPropagation()" 
                  [class.bg-[#C5A059]]="currentLayer === 'satellite'" 
                  [class.text-black]="currentLayer === 'satellite'"
                  [class.text-gray-300]="currentLayer !== 'satellite'"
                  class="px-3 py-1.5 rounded-lg text-xs font-serif font-semibold transition hover:bg-[#C5A059]/50">
            🛰️ Satélite
          </button>
        </div>

        <div class="absolute top-3 left-3 bg-[#121318]/90 border border-[#C5A059]/40 px-3 py-1.5 rounded-lg text-xs text-[#C5A059] font-serif shadow-md z-[400] pointer-events-none">
          📍 Cartagena de Indias
        </div>
      </div>

      <!-- Panel Lateral Dinámico -->
      <div class="w-full lg:w-1/3 flex flex-col justify-between bg-[#121318] p-5 rounded-xl border border-[#C5A059]/30 text-white shadow-xl relative z-20">
        <div>
          <h3 class="text-xl font-serif text-[#C5A059] mb-2 border-b border-[#C5A059]/30 pb-2">Ubicaciones Exclusivas</h3>
          
          <!-- ESTADO 1: Vista general de Sectores -->
          <div *ngIf="!selectedSector && !selectedHotel">
            <p class="text-xs text-gray-300 mb-4">Selecciona un sector o pasa el mouse por los pines:</p>
            <div class="space-y-2.5 max-h-[380px] overflow-y-auto pr-1 custom-scrollbar">
              <button *ngFor="let sector of sectors" 
                      (click)="selectSector(sector)"
                      class="w-full text-left p-3.5 rounded-xl bg-[#1A1B22] hover:bg-[#C5A059] hover:text-black transition-all duration-300 border border-gray-800 flex justify-between items-center group shadow-sm">
                <div>
                  <span class="font-serif font-semibold text-sm sm:text-base block group-hover:text-black">{{ sector.name }}</span>
                  <span class="text-xs text-gray-400 group-hover:text-gray-900 font-medium">{{ sector.hotels.length }} hoteles disponibles</span>
                </div>
                <span class="text-[#C5A059] group-hover:text-black text-sm font-bold">➔</span>
              </button>
            </div>
          </div>

          <!-- ESTADO 2: Lista de Hoteles del Sector -->
          <div *ngIf="selectedSector && !selectedHotel">
            <div class="flex justify-between items-center mb-3">
              <span class="text-xs uppercase tracking-widest text-[#C5A059] font-semibold bg-[#C5A059]/10 px-2 py-0.5 rounded border border-[#C5A059]/30">
                {{ selectedSector.name }}
              </span>
              <button (click)="selectedSector = null" class="text-gray-400 hover:text-[#C5A059] text-xs font-bold">← Volver</button>
            </div>
            <p class="text-xs text-gray-300 mb-3">Selecciona un hotel:</p>
            <div class="space-y-2 max-h-[350px] overflow-y-auto pr-1 custom-scrollbar">
              <button *ngFor="let hotel of selectedSector.hotels" 
                      (click)="selectHotel(hotel, selectedSector.name)"
                      class="w-full text-left p-3 rounded-lg bg-[#1A1B22] hover:bg-[#C5A059] hover:text-black transition border border-gray-800 flex justify-between items-center group">
                <div>
                  <span class="font-serif text-sm font-semibold block group-hover:text-black">{{ hotel.name }}</span>
                  <span class="text-xs text-[#C5A059] group-hover:text-gray-900 font-bold">{{ hotel.price }}</span>
                </div>
                <span class="text-xs group-hover:text-black">Ver ➔</span>
              </button>
            </div>
          </div>

          <!-- ESTADO 3: Ficha Detallada del Hotel -->
          <div *ngIf="selectedHotel" class="bg-[#1A1B22] border border-[#C5A059] p-4 rounded-xl shadow-inner animate-fade-in">
            <div class="flex justify-between items-start mb-2">
              <span class="text-[10px] uppercase tracking-widest text-[#C5A059] font-semibold bg-[#C5A059]/10 px-2.5 py-0.5 rounded border border-[#C5A059]/30">
                {{ selectedHotel.sector }}
              </span>
              <button (click)="selectedHotel = null" class="text-gray-400 hover:text-[#C5A059] text-sm font-bold">✕ Volver</button>
            </div>

            <h4 class="text-lg font-serif text-[#C5A059] mb-1">{{ selectedHotel.name }}</h4>
            <p class="text-gray-300 text-xs mb-3 leading-relaxed">{{ selectedHotel.description }}</p>

            <div class="grid grid-cols-2 gap-2 mb-3 text-xs">
              <div class="bg-[#121318] p-2 rounded border border-gray-800">
                <span class="text-[10px] text-gray-400 block">Capacidad</span>
                <span class="font-semibold text-white">👥 {{ selectedHotel.capacity }}</span>
              </div>
              <div class="bg-[#121318] p-2 rounded border border-gray-800">
                <span class="text-[10px] text-gray-400 block">Habitación</span>
                <span class="font-semibold text-white">🛏️ {{ selectedHotel.beds }}</span>
              </div>
            </div>

            <div class="flex justify-between items-center bg-[#121318] p-2.5 rounded border border-gray-800 mb-3">
              <span class="text-xs text-gray-400">Tarifa oficial:</span>
              <span class="text-base font-serif text-[#C5A059] font-bold">{{ selectedHotel.price }}</span>
            </div>

            <button (click)="selectedHotel = null" class="w-full bg-[#C5A059] text-black font-semibold py-2 rounded-lg hover:bg-[#b08d4b] transition text-xs shadow">
              Ver Disponibilidad
            </button>
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-gray-800/80 text-center flex justify-between items-center text-xs text-gray-400">
          <span class="italic text-[#C5A059]">Palacio del Mar</span>
          <span class="bg-[#1F2028] px-2.5 py-1 rounded border border-gray-800 text-gray-300">Conexión Segura</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    @import url('https://unpkg.com/leaflet@1.9.4/dist/leaflet.css');
    .custom-scrollbar::-webkit-scrollbar { width: 5px; }
    .custom-scrollbar::-webkit-scrollbar-track { background: #121318; }
    .custom-scrollbar::-webkit-scrollbar-thumb { background: #C5A059; border-radius: 4px; }
    .dark-mode-map .leaflet-tile-pane { filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3); }
    .leaflet-popup-content-wrapper, .leaflet-popup-tip, .custom-tooltip {
      background-color: #121318 !important;
      color: #fff !important;
      border: 1px solid #C5A059 !important;
      border-radius: 8px !important;
      font-family: serif !important;
      font-size: 12px !important;
      padding: 6px 10px !important;
    }
    :host {
      display: block;
      width: 100%;
      max-width: 1200px;
      margin: 20px auto;
      border: none !important;
      outline: none !important;
    }
    :host(*), :host(div) {
      border-color: inherit;
    }
  `]
})
export class HotelMapComponent implements AfterViewInit {
  private map: any;
  private currentTileLayer: any;
  private markers: any[] = [];
  public selectedSector: any = null;
  public selectedHotel: any = null;
  public currentLayer: string = 'streets';

  public sectors = [
    {
      name: 'Centro Histórico',
      lat: 10.4236,
      lng: -75.5536,
      zoom: 16,
      hotels: [
        { 
          name: 'Superior Patio Colonial', 
          description: 'Hermosa suite rodeada de arquitectura colonial y balcones tradicionales.', 
          price: '$450.000 COP', 
          capacity: '2 Adultos', 
          beds: '1 Cama King', 
          lat: 10.4238, 
          lng: -75.5538 
        },
        { 
          name: 'Suite Colonial Imperial', 
          description: 'Elegancia clásica frente a las plazas históricas del Centro Amurallado.', 
          price: '$620.000 COP', 
          capacity: '3 Adultos', 
          beds: '1 King + Sofa Cama', 
          lat: 10.4232, 
          lng: -75.5541 
        }
      ]
    },
    {
      name: 'Getsemaní',
      lat: 10.4190,
      lng: -75.5480,
      zoom: 16,
      hotels: [
        { 
          name: 'Casona Colonial Getsemaní', 
          description: 'Ubicada en el barrio más vibrante y cultural de Cartagena.', 
          price: '$380.000 COP', 
          capacity: '2 Adultos', 
          beds: '1 Cama Queen', 
          lat: 10.4192, 
          lng: -75.5482 
        }
      ]
    },
    {
      name: 'Bocagrande',
      lat: 10.4050,
      lng: -75.5550,
      zoom: 15,
      hotels: [
        { 
          name: 'Palacio Bocagrande Frente al Mar', 
          description: 'Exclusiva torre moderna con vista directa al mar Caribe.', 
          price: '$750.000 COP', 
          capacity: '4 Huéspedes', 
          beds: '2 Camas King', 
          lat: 10.4055, 
          lng: -75.5555 
        },
        { 
          name: 'Suite Vista Murallas', 
          description: 'Lujo contemporáneo frente a la bahía con piscina infinity.', 
          price: '$810.000 COP', 
          capacity: '2 Adultos', 
          beds: '1 Cama King', 
          lat: 10.4045, 
          lng: -75.545 
        }
      ]
    },
    {
      name: 'Manga',
      lat: 10.4160,
      lng: -75.5380,
      zoom: 15,
      hotels: [
        { 
          name: 'Reserva Manglares & Sol', 
          description: 'Refugio de paz rodeado de manglares y naturaleza exclusiva.', 
          price: '$490.000 COP', 
          capacity: '3 Huéspedes', 
          beds: '1 Queen + 1 Sencilla', 
          lat: 10.4165, 
          lng: -75.5375 
        }
      ]
    }
  ];

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.initMap();
    }, 250);
  }

  private initMap(): void {
    this.map = L.map('hotel-map', { zoomControl: false }).setView([10.415, -75.544], 13);
    
    L.control.zoom({ position: 'bottomright' }).addTo(this.map);
    L.control.scale({ position: 'bottomleft', metric: true, imperial: false }).addTo(this.map);

    this.setTileLayer('streets');
    this.renderMarkers();
  }

  public changeLayer(type: string): void {
    this.currentLayer = type;
    if (this.currentTileLayer) {
      this.map.removeLayer(this.currentTileLayer);
    }
    this.setTileLayer(type);
  }

  private setTileLayer(type: string): void {
    const mapElement = document.getElementById('hotel-map');
    if (mapElement) {
      mapElement.classList.remove('dark-mode-map');
    }

    let url = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
    let attribution = '© OpenStreetMap contributors';

    if (type === 'dark') {
      if (mapElement) {
        mapElement.classList.add('dark-mode-map');
      }
    } else if (type === 'satellite') {
      url = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
      attribution = 'Tiles © Esri';
    }

    this.currentTileLayer = L.tileLayer(url, { maxZoom: 19, attribution }).addTo(this.map);
  }

  private renderMarkers(): void {
    this.markers.forEach(m => this.map.removeLayer(m));
    this.markers = [];

    this.sectors.forEach(sector => {
      sector.hotels.forEach(hotel => {
        const marker = L.circleMarker([hotel.lat, hotel.lng], {
          color: '#C5A059', fillColor: '#121318', fillOpacity: 0.95, radius: 11, weight: 3
        }).addTo(this.map);

        marker.bindTooltip(`
          <div style="text-align:center;">
            <b style="color:#C5A059;">${hotel.name}</b><br>
            <span>${hotel.price}</span>
          </div>
        `, { direction: 'top', offset: [0, -5], className: 'custom-tooltip' });

        marker.bindPopup(`
          <div style="text-align:center; padding: 2px; max-width: 180px;">
            <b style="color:#C5A059; font-size:13px;">${hotel.name}</b><br>
            <p style="color:#ccc; font-size:11px; margin: 4px 0;">${hotel.description}</p>
            <b style="color:#C5A059; font-size:12px;">${hotel.price}</b>
          </div>
        `);

        marker.on('click', () => {
          this.selectedSector = sector;
          this.selectedHotel = { ...hotel, sector: sector.name };
        });

        this.markers.push(marker);
      });
    });
  }

  public selectSector(sector: any): void {
    this.selectedSector = sector;
    this.selectedHotel = null;
    this.map.setView([sector.lat, sector.lng], sector.zoom, { animate: true, duration: 1.2 });
  }

  public selectHotel(hotel: any, sectorName: string): void {
    this.selectedHotel = { ...hotel, sector: sectorName };
  }
}