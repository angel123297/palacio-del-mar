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
  private today = new Date();
  
  // Default: checkIn tomorrow, checkOut in 3 days
  private defaultCheckIn = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate() + 1, 12, 0, 0);
  private defaultCheckOut = new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate() + 3, 12, 0, 0);

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
    return parse(dateStr, 'yyyy-MM-dd', new Date(this.today.getFullYear(), this.today.getMonth(), this.today.getDate(), 12, 0, 0));
  }

  // Computed helpful signals
  nights = computed(() => {
    const start = this.parseToMidday(this.filters().checkIn);
    const end = this.parseToMidday(this.filters().checkOut);
    if (isNaN(start.getTime()) || isNaN(end.getTime())) return 1;
    const diffTime = Math.abs(end.getTime() - start.getTime());
    const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24)); 
    return diffDays > 0 ? diffDays : 1;
  });

  updateDates(checkIn: Date, checkOut: Date) {
    this.state.update(s => ({
      ...s,
      checkIn: format(checkIn, 'yyyy-MM-dd'),
      checkOut: format(checkOut, 'yyyy-MM-dd')
    }));
  }

  updateBranch(branchSlug: string) {
    this.state.update(s => ({ ...s, branchSlug }));
  }

  updateGuests(adults: number, children: number) {
    this.state.update(s => ({ ...s, adults, children }));
  }

  updateVibe(vibe: string) {
    this.state.update(s => ({ ...s, vibe }));
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

