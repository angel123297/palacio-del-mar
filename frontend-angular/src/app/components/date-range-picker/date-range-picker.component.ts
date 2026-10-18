import { Component, Input, Output, EventEmitter, ElementRef, HostListener, OnInit, OnChanges, SimpleChanges, computed, signal, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/api.service';

const MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const WEEKDAYS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];
const MAX_NIGHTS = 90;
const MAX_YEAR = 2030;

const pad = (n: number) => String(n).padStart(2, '0');
const toISO = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;
const addDays = (iso: string, n: number) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
};
const localToday = () => {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  } catch {
    const t = new Date();
    return toISO(t.getFullYear(), t.getMonth() + 1, t.getDate());
  }
};
const nightsBetween = (a: string, b: string) => Math.round((new Date(b).getTime() - new Date(a).getTime()) / 86400000);
const prettyDate = (iso: string) => {
  if (!iso) return 'Elegir fecha';
  const [y, m, d] = iso.split('-').map(Number);
  return `${d} ${MONTHS[m - 1].slice(0, 3)} ${y}`;
};
const shortDate = (iso: string) => {
  const [, m, d] = iso.split('-').map(Number);
  return `${d} ${MONTHS[m - 1].slice(0, 3).toLowerCase()}`;
};
const shortRange = (a: string, b: string) => (a === b ? shortDate(a) : `${shortDate(a)} \u2013 ${shortDate(b)}`);

@Component({
  selector: 'app-date-range-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './date-range-picker.component.html',
  styleUrls: ['./date-range-picker.component.css']
})
export class DateRangePickerComponent implements OnInit, OnChanges {
  @Input() checkIn: string = '';
  @Input() checkOut: string = '';
  @Input() guests: number = 1;
  @Input() branch: string = '';
  @Input() suiteId: string = '';
  @Output() rangeChange = new EventEmitter<{checkIn: string, checkOut: string}>();

  api = inject(ApiService);
  eRef = inject(ElementRef);

  today = localToday();
  open = signal(false);
  
  view = signal({ year: Number(this.today.slice(0, 4)), month: Number(this.today.slice(5, 7)) });
  step = signal<'in' | 'out'>('in');
  
  soldOut = signal<Record<string, boolean>>({});
  dayAvailability = signal<Record<string, { soldOut: boolean, availableSuites: number, totalSuites: number, occupancyRate?: number }>>({});
  loaded = signal<Record<string, boolean>>({});
  peakRanges = signal<any[]>([]);
  holidays = signal<any[]>([]);
  peakYears = signal<Record<number, boolean>>({});
  promoMonths = signal<Record<string, any[]>>({});
  loading = signal(false);
  
  private reqId = 0;

  MONTHS = MONTHS;
  WEEKDAYS = WEEKDAYS;
  MAX_YEAR = MAX_YEAR;

  constructor() {
    effect(() => {
      if (this.open()) {
        const v = this.view();
        this.loadMonth(v.year, v.month);
        this.loadPromos(v.year, v.month);
        this.loadPeak(v.year);
        this.loadPeak(v.year - 1);
      }
    });
  }

  ngOnInit() {
    const base = this.checkIn || this.today;
    this.view.set({ year: Number(base.slice(0, 4)), month: Number(base.slice(5, 7)) });
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['guests'] || changes['branch'] || changes['suiteId']) {
      this.reqId++;
      this.soldOut.set({});
      this.dayAvailability.set({});
      this.loaded.set({});
      this.promoMonths.set({});
      if (this.open()) {
        const v = this.view();
        this.loadMonth(v.year, v.month);
        this.loadPromos(v.year, v.month);
      }
    }
  }

  @HostListener('document:mousedown', ['$event'])
  onDown(e: Event) {
    if (this.open() && !this.eRef.nativeElement.contains(e.target)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onKey() {
    this.open.set(false);
  }

  async loadMonth(year: number, month: number) {
    const currentReq = this.reqId;
    const key = `${this.guests}:${this.branch}:${this.suiteId}:${year}-${month}`;
    if (this.loaded()[key]) return;
    this.loading.set(true);
    try {
      const params: any = { year, month, guests: this.guests };
      if (this.branch) params.branch = this.branch;
      if (this.suiteId) params.suiteId = this.suiteId;
      this.api.get<any>('/availability/monthly', params).subscribe({
        next: (res) => {
          if (this.reqId !== currentReq) return;
          const days = res?.data?.calendar || res?.calendar || [];
          const nextSoldOut = { ...this.soldOut() };
          const nextAvail = { ...this.dayAvailability() };
          days.forEach((d: any) => {
            if (d.soldOut) nextSoldOut[d.date] = true;
            nextAvail[d.date] = {
              soldOut: !!d.soldOut,
              availableSuites: d.availableSuites,
              totalSuites: d.totalSuites,
              occupancyRate: d.occupancyRate
            };
          });
          this.soldOut.set(nextSoldOut);
          this.dayAvailability.set(nextAvail);
          this.loaded.set({ ...this.loaded(), [key]: true });
          this.loading.set(false);
        },
        error: () => {
          if (this.reqId !== currentReq) return;
          this.loading.set(false);
        }
      });
    } catch {
      this.loading.set(false);
    }
  }

  async loadPeak(year: number) {
    const currentReq = this.reqId;
    if (this.peakYears()[year]) return;
    this.peakYears.set({ ...this.peakYears(), [year]: true });
    this.api.get<any>('/availability/peak-dates', { year }).subscribe({
      next: (res) => {
        if (this.reqId !== currentReq) return;
        const d = res?.data || res || {};
        const ranges = [...(d.highSeason || []), ...(d.peakSeason || [])];
        this.peakRanges.set([...this.peakRanges(), ...ranges]);
        this.holidays.set([...this.holidays(), ...(d.holidays || [])]);
      },
      error: () => {
        if (this.reqId !== currentReq) return;
        const next = { ...this.peakYears() };
        delete next[year];
        this.peakYears.set(next);
      }
    });
  }

  async loadPromos(year: number, month: number) {
    const currentReq = this.reqId;
    const key = `${year}-${month}`;
    if (this.promoMonths()[key]) return;
    this.promoMonths.set({ ...this.promoMonths(), [key]: [] });
    const params: any = { year, month };
    if (this.branch) params.branch = this.branch;
    this.api.get<any>('/availability/promotions', params).subscribe({
      next: (res) => {
        if (this.reqId !== currentReq) return;
        const p = Array.isArray(res?.data) ? res.data : (res?.data?.promotions || res?.promotions || (Array.isArray(res) ? res : []));
        this.promoMonths.set({ ...this.promoMonths(), [key]: p });
      },
      error: () => {
        if (this.reqId !== currentReq) return;
        const next = { ...this.promoMonths() };
        delete next[key];
        this.promoMonths.set(next);
      }
    });
  }

  isPeak(iso: string) {
    return this.peakRanges().some(r => iso >= r.start && iso <= r.end);
  }

  promosByDay = computed(() => {
    const map: Record<string, any[]> = {};
    const seen = new Set();
    Object.values(this.promoMonths()).flat().forEach(p => {
      if (!p || seen.has(p.id)) return;
      seen.add(p.id);
      if (!p.startDate || !p.endDate || p.startDate > p.endDate) return;
      let count = 0;
      for (let d = p.startDate; d <= p.endDate && count < 366; d = addDays(d, 1)) {
        (map[d] = map[d] || []).push(p);
        count++;
      }
    });
    return map;
  });

  holidayByDay = computed(() => {
    const map: Record<string, string> = {};
    this.holidays().forEach(h => { map[h.date] = h.name; });
    return map;
  });

  monthItems = computed(() => {
    const v = this.view();
    const first = toISO(v.year, v.month, 1);
    const last = toISO(v.year, v.month, new Date(Date.UTC(v.year, v.month, 0)).getUTCDate());
    const items: any[] = [];
    const seen = new Set();
    const promos = this.promoMonths()[`${v.year}-${v.month}`] || [];
    [...promos].sort((a, b) => b.discountPercent - a.discountPercent).forEach(p => {
      items.push({ key: `p${p.id}`, kind: 'promo', name: p.title, sub: p.branch?.name, when: shortRange(p.startDate, p.endDate), badge: `-${p.discountPercent}%` });
    });
    this.peakRanges().filter(r => r.start <= last && r.end >= first).forEach(r => {
      const k = `s${r.name}${r.start}`;
      if (seen.has(k)) return;
      seen.add(k);
      items.push({ key: k, kind: 'season', name: r.name, sub: 'Temporada alta', when: shortRange(r.start, r.end) });
    });
    this.holidays().filter(h => h.date >= first && h.date <= last).forEach(h => {
      const k = `h${h.date}`;
      if (seen.has(k)) return;
      seen.add(k);
      items.push({ key: k, kind: 'holiday', name: h.name, sub: 'Festivo', when: shortDate(h.date) });
    });
    return items;
  });

  canCheckOut(inISO: string, outISO: string): boolean {
    if (outISO <= inISO) return false;
    const n = nightsBetween(inISO, outISO);
    if (n > MAX_NIGHTS) return false;
    for (let i = 0; i < n; i++) {
      const d = addDays(inISO, i);
      if (this.soldOut()[d]) return false;
    }
    return true;
  }

  isDayDisabled(iso: string): boolean {
    if (iso < this.today) return true;
    if (this.step() === 'in' || !this.checkIn) {
      return !!this.soldOut()[iso];
    }
    if (iso <= this.checkIn) {
      // allow clicking to reset check-in date
      return !!this.soldOut()[iso];
    }
    return !this.canCheckOut(this.checkIn, iso);
  }

  pick(iso: string) {
    if (this.soldOut()[iso] || iso < this.today) return;

    if (this.step() === 'out' && this.checkIn) {
      if (iso > this.checkIn && this.canCheckOut(this.checkIn, iso)) {
        this.rangeChange.emit({ checkIn: this.checkIn, checkOut: iso });
        this.open.set(false);
        this.step.set('in');
        return;
      } else if (iso <= this.checkIn) {
        // Change check-in date directly
        this.rangeChange.emit({ checkIn: iso, checkOut: '' });
        this.step.set('out');
        return;
      }
    }

    this.rangeChange.emit({ checkIn: iso, checkOut: '' });
    this.step.set('out');
  }

  shift(delta: number) {
    const v = this.view();
    let m = v.month + delta;
    let y = v.year;
    if (m < 1) { m = 12; y -= 1; }
    if (m > 12) { m = 1; y += 1; }
    this.view.set({ year: y, month: m });
  }

  get atMin() {
    const todayY = Number(this.today.slice(0, 4));
    const todayM = Number(this.today.slice(5, 7));
    return this.view().year === todayY && this.view().month === todayM;
  }
  
  get atMax() {
    return this.view().year >= MAX_YEAR && this.view().month === 12;
  }

  get cells() {
    const v = this.view();
    const firstWeekday = (new Date(Date.UTC(v.year, v.month - 1, 1)).getUTCDay() + 6) % 7;
    const daysInMonth = new Date(Date.UTC(v.year, v.month, 0)).getUTCDate();
    return [
      ...Array.from({ length: firstWeekday }, () => null),
      ...Array.from({ length: daysInMonth }, (_, i) => toISO(v.year, v.month, i + 1))
    ];
  }

  openPicker(which: 'in' | 'out') {
    this.step.set(which);
    if (which === 'out' && !this.checkIn) this.step.set('in');
    this.open.set(true);
  }

  dayTitle(iso: string, full: boolean) {
    const lines = [];
    if (iso < this.today) {
      lines.push('Fecha pasada no disponible');
      return lines.join('\n');
    }
    const info = this.dayAvailability()[iso];
    if (full) {
      lines.push('❌ Ocupado / Sin disponibilidad para estas fechas');
    } else {
      if (info && info.availableSuites !== undefined) {
        lines.push(`✅ Disponible (${info.availableSuites} ${info.availableSuites === 1 ? 'habitación disponible' : 'habitaciones disponibles'})`);
      } else {
        lines.push('✅ Disponible para reserva');
      }
    }

    if (this.step() === 'out' && this.checkIn) {
      if (iso > this.checkIn && !this.canCheckOut(this.checkIn, iso)) {
        lines.push('⚠️ No se puede seleccionar como salida: hay noches intermedias ocupadas.');
      } else if (iso === this.checkIn) {
        lines.push('📍 Día de llegada seleccionado (haz clic para cambiar)');
      }
    }

    (this.promosByDay()[iso] || []).forEach(p =>
      lines.push(`💎 Promoción: -${p.discountPercent}% · ${p.title}${p.branch?.name ? ` (${p.branch.name})` : ''}`)
    );
    if (this.holidayByDay()[iso]) lines.push(`⭐ Festivo: ${this.holidayByDay()[iso]}`);
    if (!full && this.isPeak(iso)) lines.push('🔥 Temporada alta');
    return lines.length ? lines.join('\n') : null;
  }

  prettyDate(iso: string) { return prettyDate(iso); }
}
