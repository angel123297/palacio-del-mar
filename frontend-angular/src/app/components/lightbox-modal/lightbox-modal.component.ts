import { Component, Input, Output, EventEmitter, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-lightbox-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="isOpen && images.length > 0" class="fixed inset-0 z-[300] bg-black/95 flex flex-col items-center justify-center p-4 backdrop-blur-md animate-fade-in" role="dialog" aria-modal="true" aria-label="Visor de Fotografías de Suite">
      <!-- Barra superior de controles -->
      <div class="absolute top-4 right-4 flex items-center gap-4 z-10">
        <span class="text-xs uppercase tracking-widest text-[#c9a96e] font-mono">
          {{ safeIndex() + 1 }} / {{ images.length }}
        </span>
        <button (click)="close()" aria-label="Cerrar visor" class="text-white hover:text-[#c9a96e] text-3xl font-light w-10 h-10 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/20 transition-all cursor-pointer">
          ✕
        </button>
      </div>

      <!-- Imagen activa -->
      <div class="relative max-w-5xl max-h-[80vh] flex items-center justify-center overflow-hidden">
        <img [src]="images[safeIndex()]" [alt]="title || 'Fotografía de la Suite'" class="max-w-full max-h-[80vh] object-contain rounded-lg shadow-2xl transition-all duration-300 select-none" />
      </div>

      <!-- Controles de navegación -->
      <button *ngIf="images.length > 1" (click)="prev()" aria-label="Foto anterior" class="absolute left-4 top-1/2 -translate-y-1/2 w-12 h-12 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/25 text-white hover:text-[#c9a96e] text-2xl transition-all cursor-pointer z-10">
        ‹
      </button>
      <button *ngIf="images.length > 1" (click)="next()" aria-label="Foto siguiente" class="absolute right-4 top-1/2 -translate-y-1/2 w-12 h-12 flex items-center justify-center rounded-full bg-white/10 hover:bg-white/25 text-white hover:text-[#c9a96e] text-2xl transition-all cursor-pointer z-10">
        ›
      </button>

      <!-- Miniaturas inferiores -->
      <div *ngIf="images.length > 1" class="absolute bottom-4 max-w-xl flex items-center gap-2 overflow-x-auto p-2 bg-black/60 rounded-xl border border-white/10">
        <button *ngFor="let img of images; let idx = index" (click)="currentIndex = idx" [class.border-[#c9a96e]]="safeIndex() === idx" class="w-12 h-12 rounded overflow-hidden border-2 border-transparent hover:opacity-100 transition-all flex-shrink-0 cursor-pointer" [class.opacity-50]="safeIndex() !== idx">
          <img [src]="img" alt="Miniatura" class="w-full h-full object-cover" />
        </button>
      </div>
    </div>
  `
})
export class LightboxModalComponent {
  @Input() images: string[] = [];
  @Input() currentIndex = 0;
  @Input() isOpen = false;
  @Input() title?: string;

  @Output() closed = new EventEmitter<void>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen) {
      this.close();
    }
  }

  @HostListener('document:keydown.arrowleft')
  onArrowLeft(): void {
    if (this.isOpen) {
      this.prev();
    }
  }

  @HostListener('document:keydown.arrowright')
  onArrowRight(): void {
    if (this.isOpen) {
      this.next();
    }
  }

  safeIndex(): number {
    if (!this.images || this.images.length === 0) return 0;
    return Math.max(0, Math.min(this.currentIndex, this.images.length - 1));
  }

  close(): void {
    this.closed.emit();
  }

  next(): void {
    if (!this.images || this.images.length === 0) return;
    this.currentIndex = (this.safeIndex() + 1) % this.images.length;
  }

  prev(): void {
    if (!this.images || this.images.length === 0) return;
    this.currentIndex = (this.safeIndex() - 1 + this.images.length) % this.images.length;
  }
}
