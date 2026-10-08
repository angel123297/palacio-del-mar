import { Component, Input, Output, EventEmitter, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-custom-datepicker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="cdp-container">
      <div class="cdp-input" (click)="toggle()">
        <div class="cdp-label">{{ label }}</div>
        <div class="cdp-value">{{ date | date:'dd/MM/yyyy' }}</div>
        <div class="cdp-sub">{{ subLabel }}</div>
      </div>
      
      <div class="cdp-dropdown" *ngIf="isOpen">
        <div class="cdp-header">
          <button type="button" (click)="changeMonth(-1)">&larr;</button>
          <span>{{ currentMonthName }} {{ currentYear }}</span>
          <button type="button" (click)="changeMonth(1)">&rarr;</button>
        </div>
        <div class="cdp-grid">
          <div class="cdp-day-name" *ngFor="let d of ['D','L','M','M','J','V','S']">{{d}}</div>
          <div class="cdp-day empty" *ngFor="let empty of emptyDays"></div>
          <div class="cdp-day" *ngFor="let day of daysInMonth" 
               [class.selected]="isSelected(day)"
               (click)="selectDate(day); $event.stopPropagation()">
            {{ day }}
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .cdp-container { position: relative; border: 1px solid rgba(212,175,55,0.2); border-radius: 6px; padding: 12px; cursor: pointer; background: rgba(0,0,0,0.5); flex: 1; min-width: 150px; }
    .cdp-container:hover { border-color: var(--gold); }
    .cdp-label { font-size: 0.65rem; color: var(--gold); text-transform: uppercase; letter-spacing: 2px; font-weight: 700; }
    .cdp-value { font-size: 1.15rem; color: #fff; margin: 6px 0; font-family: var(--font-title); }
    .cdp-sub { font-size: 0.7rem; color: #aaa; }
    
    .cdp-dropdown { position: absolute; top: calc(100% + 10px); left: 0; background: #222; border: 1px solid rgba(212,175,55,0.3); border-radius: 8px; padding: 15px; width: 280px; z-index: 999; box-shadow: 0 10px 25px rgba(0,0,0,0.8); }
    .cdp-header { display: flex; justify-content: space-between; align-items: center; color: #fff; font-weight: bold; margin-bottom: 10px; }
    .cdp-header button { background: none; border: none; color: var(--gold); cursor: pointer; font-size: 1.2rem; }
    .cdp-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 5px; text-align: center; }
    .cdp-day-name { font-size: 0.8rem; color: #888; margin-bottom: 5px; }
    .cdp-day { padding: 6px 0; font-size: 0.9rem; color: #ddd; border-radius: 4px; cursor: pointer; }
    .cdp-day:hover:not(.empty) { background: rgba(212,175,55,0.2); }
    .cdp-day.selected { background: var(--gold); color: #000; font-weight: bold; }
  `]
})
export class CustomDatepickerComponent {
  @Input() label = 'Fecha';
  @Input() subLabel = '';
  @Input() date: string = new Date().toISOString().split('T')[0];
  @Output() dateChange = new EventEmitter<string>();
  
  isOpen = false;
  currentDate = new Date();
  
  get currentMonthName(): string {
    const months = ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'];
    return months[this.currentDate.getMonth()];
  }
  
  get currentYear(): number {
    return this.currentDate.getFullYear();
  }
  
  get emptyDays(): number[] {
    const firstDay = new Date(this.currentYear, this.currentDate.getMonth(), 1).getDay();
    return Array(firstDay).fill(0);
  }
  
  get daysInMonth(): number[] {
    const days = new Date(this.currentYear, this.currentDate.getMonth() + 1, 0).getDate();
    return Array.from({length: days}, (_, i) => i + 1);
  }
  
  constructor(private eRef: ElementRef) {}
  
  ngOnInit() {
    this.currentDate = new Date(this.date + 'T12:00:00');
  }
  
  @HostListener('document:click', ['$event'])
  clickout(event: any) {
    if(!this.eRef.nativeElement.contains(event.target)) {
      this.isOpen = false;
    }
  }
  
  toggle() {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.currentDate = new Date(this.date + 'T12:00:00');
    }
  }
  
  changeMonth(dir: number) {
    this.currentDate = new Date(this.currentYear, this.currentDate.getMonth() + dir, 1);
  }
  
  selectDate(day: number) {
    const d = new Date(this.currentYear, this.currentDate.getMonth(), day, 12);
    this.date = d.toISOString().split('T')[0];
    this.dateChange.emit(this.date);
    this.isOpen = false;
  }
  
  isSelected(day: number): boolean {
    const d = new Date(this.currentYear, this.currentDate.getMonth(), day, 12).toISOString().split('T')[0];
    return d === this.date;
  }
}
