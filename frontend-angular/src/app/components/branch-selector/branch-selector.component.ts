import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';
import { Branch } from '../../models/types';

@Component({
  selector: 'app-branch-selector',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="branches-section">
      <div class="container">
        <div class="section-title">
          <p class="subtitle">NUESTRAS UBICACIONES EXCLUSIVAS</p>
          <h2>Elige tu Palacio en Cartagena</h2>
        </div>

        <div class="branches-grid">
          <div 
            *ngFor="let branch of branches" 
            class="branch-card"
            [class.active]="selectedSlug === branch.slug"
            (click)="selectBranch(branch.slug)"
          >
            <div class="branch-image-wrapper">
              <img [src]="branch.mainImage || getFallbackImage(branch.slug)" [alt]="branch.name" />
              <div class="branch-overlay"></div>
              <span class="branch-badge">{{ branch.zone }}</span>
            </div>
            <div class="branch-content">
              <h3>{{ branch.name }}</h3>
              <p class="branch-tagline">{{ branch.tagline }}</p>
              <div class="branch-vibes">
                <span *ngFor="let v of branch.vibe" class="vibe-tag">#{{ v }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
  styles: [`
    .branches-section {
      padding: 4rem 1.5rem;
      background: #0d0d0d;
      border-bottom: 1px solid rgba(212, 175, 55, 0.15);
    }
    .container {
      max-width: 1200px;
      margin: 0 auto;
    }
    .section-title {
      text-align: center;
      margin-bottom: 3rem;
    }
    .subtitle {
      color: #d4af37;
      letter-spacing: 2px;
      font-size: 0.85rem;
      font-weight: 700;
      margin-bottom: 8px;
    }
    .section-title h2 {
      color: #ffffff;
      font-size: 2.2rem;
      font-family: 'Playfair Display', serif, var(--font-title);
    }
    .branches-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 1.5rem;
    }
    .branch-card {
      background: rgba(255, 255, 255, 0.02);
      border: 1px solid rgba(212, 175, 55, 0.2);
      border-radius: 12px;
      overflow: hidden;
      cursor: pointer;
      transition: all 0.3s ease;
    }
    .branch-card:hover, .branch-card.active {
      border-color: #d4af37;
      transform: translateY(-4px);
      box-shadow: 0 10px 30px rgba(212, 175, 55, 0.15);
    }
    .branch-image-wrapper {
      height: 180px;
      position: relative;
      overflow: hidden;
    }
    .branch-image-wrapper img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.5s ease;
    }
    .branch-card:hover .branch-image-wrapper img {
      transform: scale(1.08);
    }
    .branch-overlay {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, transparent 40%, rgba(13,13,13,0.8) 100%);
    }
    .branch-badge {
      position: absolute;
      top: 12px;
      left: 12px;
      background: rgba(13, 13, 13, 0.8);
      color: #d4af37;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 600;
      border: 1px solid rgba(212, 175, 55, 0.3);
    }
    .branch-content {
      padding: 1.25rem;
    }
    .branch-content h3 {
      color: #ffffff;
      font-size: 1.15rem;
      margin: 0 0 6px 0;
    }
    .branch-tagline {
      color: #aaaaaa;
      font-size: 0.85rem;
      line-height: 1.4;
      margin-bottom: 1rem;
    }
    .branch-vibes {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }
    .vibe-tag {
      color: #d4af37;
      font-size: 0.75rem;
      background: rgba(212, 175, 55, 0.08);
      padding: 2px 8px;
      border-radius: 4px;
    }
  `]
})
export class BranchSelectorComponent implements OnInit {
  branches: Branch[] = [];
  selectedSlug: string = '';

  @Output() branchSelected = new EventEmitter<string>();

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<any>('/branches').subscribe({
      next: (res) => {
        this.branches = res.data || res;
      },
      error: () => {}
    });
  }

  selectBranch(slug: string): void {
    this.selectedSlug = this.selectedSlug === slug ? '' : slug;
    this.branchSelected.emit(this.selectedSlug);
  }

  getFallbackImage(slug: string): string {
    const images: Record<string, string> = {
      'centro-historico': 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800',
      'getsemani': 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800',
      'bocagrande': 'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800',
      'la-boquilla': 'https://images.unsplash.com/photo-1540518614846-7eded433c457?w=800'
    };
    return images[slug] || 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800';
  }
}
