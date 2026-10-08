import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-hero',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="hero-section">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <span class="hero-badge">HOTEL BOUTIQUE & RESORT 5★</span>
        <h1 class="hero-title">Lujo y Confort Frente al Mar Caribe</h1>
        <p class="hero-subtitle">Vive una experiencia inolvidable en las mejores ubicaciones de Cartagena de Indias</p>
        
        <div class="hero-search-bar">
          <div class="search-field">
            <label>Sede</label>
            <select>
              <option value="">Todas las Sedes</option>
              <option value="centro-historico">Centro Histórico</option>
              <option value="getsemani">Getsemaní</option>
              <option value="bocagrande">Bocagrande</option>
              <option value="la-boquilla">La Boquilla</option>
            </select>
          </div>
          <div class="search-field">
            <label>Llegada</label>
            <input type="date" />
          </div>
          <div class="search-field">
            <label>Salida</label>
            <input type="date" />
          </div>
          <div class="search-field">
            <label>Huéspedes</label>
            <select>
              <option value="1">1 Huésped</option>
              <option value="2" selected>2 Huéspedes</option>
              <option value="3">3 Huéspedes</option>
              <option value="4">4+ Huéspedes</option>
            </select>
          </div>
          <button class="search-btn">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            Buscar Suites
          </button>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .hero-section {
      position: relative;
      min-height: 85vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: url('https://images.unsplash.com/photo-1566073771259-6a8506099945?w=1600&auto=format&fit=crop&q=80') center/cover no-repeat;
      padding: 4rem 1.5rem;
      color: #ffffff;
      text-align: center;
    }
    .hero-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, rgba(13, 13, 13, 0.6) 0%, rgba(13, 13, 13, 0.85) 100%);
    }
    .hero-content {
      position: relative;
      z-index: 1;
      max-width: 900px;
    }
    .hero-badge {
      display: inline-block;
      color: #d4af37;
      font-size: 0.85rem;
      letter-spacing: 3px;
      font-weight: 700;
      margin-bottom: 1rem;
      text-transform: uppercase;
      background: rgba(212, 175, 55, 0.1);
      padding: 6px 16px;
      border-radius: 20px;
      border: 1px solid rgba(212, 175, 55, 0.3);
    }
    .hero-title {
      font-family: 'Playfair Display', serif, var(--font-title);
      font-size: 3.2rem;
      font-weight: 700;
      color: #ffffff;
      line-height: 1.15;
      margin-bottom: 1.25rem;
      text-shadow: 0 4px 20px rgba(0,0,0,0.5);
    }
    .hero-subtitle {
      font-size: 1.2rem;
      color: #e0e0e0;
      margin-bottom: 2.5rem;
      font-weight: 300;
    }
    .hero-search-bar {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)) auto;
      gap: 12px;
      background: rgba(13, 13, 13, 0.85);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(212, 175, 55, 0.3);
      padding: 1.25rem;
      border-radius: 14px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.6);
    }
    .search-field {
      display: flex;
      flex-direction: column;
      text-align: left;
    }
    .search-field label {
      font-size: 0.75rem;
      text-transform: uppercase;
      color: #d4af37;
      margin-bottom: 4px;
      font-weight: 600;
      letter-spacing: 1px;
    }
    .search-field select, .search-field input {
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.15);
      color: #ffffff;
      padding: 10px 12px;
      border-radius: 6px;
      font-size: 0.9rem;
      outline: none;
    }
    .search-btn {
      display: flex;
      align-items: center;
      gap: 8px;
      background: linear-gradient(135deg, #d4af37 0%, #aa8625 100%);
      color: #0d0d0d;
      font-weight: 700;
      border: none;
      padding: 0 24px;
      border-radius: 8px;
      cursor: pointer;
      font-size: 0.95rem;
      height: 44px;
      align-self: flex-end;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .search-btn:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(212, 175, 55, 0.4);
    }
    @media (max-width: 768px) {
      .hero-title { font-size: 2.2rem; }
      .hero-search-bar { grid-template-columns: 1fr; }
      .search-btn { width: 100%; justify-content: center; }
    }
  `]
})
export class HeroComponent {}
