import { Component, Input, Output, EventEmitter, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-custom-guestpicker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cgp-container">
      <div class="cgp-input" (click)="toggle()">
        <div class="cgp-label">Huéspedes</div>
        <div class="cgp-value">{{ adults }} {{ adults === 1 ? 'adulto' : 'adultos' }}</div>
        <div class="cgp-sub">Hasta {{ maxGuests }} en total</div>
      </div>
      
      <div class="cgp-dropdown" *ngIf="isOpen" (click)="$event.stopPropagation()">
        <div class="cgp-row">
          <span>Adultos</span>
          <div class="cgp-controls">
            <button type="button" (click)="changeGuests(-1)" [disabled]="adults <= 1">-</button>
            <span>{{ adults }}</span>
            <button type="button" (click)="changeGuests(1)" [disabled]="adults >= maxGuests">+</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .cgp-container { position: relative; border: 1px solid rgba(212,175,55,0.2); border-radius: 6px; padding: 12px; cursor: pointer; background: rgba(0,0,0,0.5); flex: 1; min-width: 150px; }
    .cgp-container:hover { border-color: var(--gold); }
    .cgp-label { font-size: 0.65rem; color: var(--gold); text-transform: uppercase; letter-spacing: 2px; font-weight: 700; }
    .cgp-value { font-size: 1.15rem; color: #fff; margin: 6px 0; font-family: var(--font-title); }
    .cgp-sub { font-size: 0.7rem; color: #aaa; }
    
    .cgp-dropdown { position: absolute; top: calc(100% + 10px); right: 0; background: #222; border: 1px solid rgba(212,175,55,0.3); border-radius: 8px; padding: 15px; width: 220px; z-index: 999; box-shadow: 0 10px 25px rgba(0,0,0,0.8); }
    .cgp-row { display: flex; justify-content: space-between; align-items: center; color: #fff; font-weight: bold; }
    .cgp-controls { display: flex; align-items: center; gap: 15px; }
    .cgp-controls button { background: none; border: 1px solid var(--gold); color: var(--gold); cursor: pointer; font-size: 1.2rem; border-radius: 50%; width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; }
    .cgp-controls button:disabled { opacity: 0.3; cursor: not-allowed; }
  `]
})
export class CustomGuestpickerComponent {
  @Input() maxGuests = 2;
  @Input() adults = 2;
  @Output() adultsChange = new EventEmitter<number>();
  
  isOpen = false;
  
  constructor(private eRef: ElementRef) {}
  
  @HostListener('document:click', ['$event'])
  clickout(event: any) {
    if(!this.eRef.nativeElement.contains(event.target)) {
      this.isOpen = false;
    }
  }
  
  toggle() {
    this.isOpen = !this.isOpen;
  }
  
  changeGuests(dir: number) {
    const newVal = this.adults + dir;
    if (newVal >= 1 && newVal <= this.maxGuests) {
      this.adults = newVal;
      this.adultsChange.emit(this.adults);
    }
  }
}
