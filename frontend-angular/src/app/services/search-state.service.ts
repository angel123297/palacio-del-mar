import { Injectable, signal, computed } from '@angular/core';
import { format, parse } from 'date-fns';

export interface SearchFilters {
  checkIn: string; // Format: 'yyyy-MM-dd'
  checkOut: string; // Format: 'yyyy-MM-dd'
  branchSlug: string;
  adults: number;
  children: number;
  vibe: string;
}

@Injectable({ providedIn: 'root' })
export class SearchStateService {
  public get today(): Date {
    return this.getToday();
  }

  private getToday(): Date {
    try {
      const iso = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
      const [y, m, d] = iso.split('-').map(Number);
      return new Date(y, m - 1, d, 12, 0, 0);
    } catch {
      return new Date();
    }
  }

  // Default: checkIn tomorrow, checkOut in 3 days
  private get defaultCheckIn(): Date {
    const t = this.getToday();
    return new Date(t.getFullYear(), t.getMonth(), t.getDate() + 1, 12, 0, 0);
  }
  private get defaultCheckOut(): Date {
    const t = this.getToday();
    return new Date(t.getFullYear(), t.getMonth(), t.getDate() + 3, 12, 0, 0);
  }

  private state = signal<SearchFilters>({
    checkIn: format(this.defaultCheckIn, 'yyyy-MM-dd'),
    checkOut: format(this.defaultCheckOut, 'yyyy-MM-dd'),
    branchSlug: '',
    adults: 2,
    children: 0,
    vibe: ''
  });

  // Readonly access to state
  filters = this.state.asReadonly();

  // Helper to parse 'yyyy-MM-dd' safely into a midday local Date
  public parseToMidday(dateStr: string): Date {
    const t = this.getToday();
    return parse(dateStr, 'yyyy-MM-dd', new Date(t.getFullYear(), t.getMonth(), t.getDate(), 12, 0, 0));
  }

  // Computed helpful signals
  nights = computed(() => {
    const start = this.parseToMidday(this.filters().checkIn);
    const end = this.parseToMidday(this.filters().checkOut);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0;
    // Sin Math.abs: un rango invertido (salida antes que llegada) no cuenta noches positivas.
    const diffTime = end.getTime() - start.getTime();
    const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
    return diffDays > 0 ? diffDays : 0;
  });

  updateDates(checkIn: Date, checkOut: Date) {
    this.state.update(s => ({
      ...s,
      checkIn: format(checkIn, 'yyyy-MM-dd'),
      checkOut: format(checkOut, 'yyyy-MM-dd')
    }));
  }

  updateRawDates(checkIn: string, checkOut: string) {
    let effectiveCheckOut = checkOut;
    if (checkIn && (!effectiveCheckOut || effectiveCheckOut <= checkIn)) {
      const start = this.parseToMidday(checkIn);
      if (!isNaN(start.getTime())) {
        const nextDay = new Date(start.getFullYear(), start.getMonth(), start.getDate() + 1, 12, 0, 0);
        effectiveCheckOut = format(nextDay, 'yyyy-MM-dd');
      }
    }
    this.state.update(s => ({
      ...s,
      checkIn,
      checkOut: effectiveCheckOut
    }));
  }

  updateBranch(branchSlug: string) {
    this.state.update(s => ({ ...s, branchSlug }));
  }

  updateGuests(adults: number, children: number) {
    const validAdults = Math.max(1, Number.isFinite(adults) ? adults : 1);
    const validChildren = Math.max(0, Number.isFinite(children) ? children : 0);
    this.state.update(s => ({ ...s, adults: validAdults, children: validChildren }));
  }

  updateVibe(vibe: string) {
    this.state.update(s => ({ ...s, vibe }));
  }

  reset() {
    this.state.set({
      checkIn: format(this.defaultCheckIn, 'yyyy-MM-dd'),
      checkOut: format(this.defaultCheckOut, 'yyyy-MM-dd'),
      branchSlug: '',
      adults: 2,
      children: 0,
      vibe: ''
    });
  }

  getApiParams(): Record<string, string | number> {
    const f = this.filters();
    const params: Record<string, string | number> = {
      checkIn: f.checkIn,
      checkOut: f.checkOut,
      guests: f.adults + f.children
    };
    if (f.branchSlug) {
      params['branch'] = f.branchSlug;
    }
    if (f.vibe) {
      params['vibe'] = f.vibe;
    }
    return params;
  }
}

