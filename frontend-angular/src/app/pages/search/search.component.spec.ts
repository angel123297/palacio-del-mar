import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchComponent } from './search.component';
import { ApiService } from '../../services/api.service';
import { SearchStateService } from '../../services/search-state.service';
import { CurrencyService } from '../../services/currency.service';
import { of } from 'rxjs';

describe('SearchComponent fetchResults (F18)', () => {
  let component: SearchComponent;
  let fixture: ComponentFixture<SearchComponent>;
  let apiGet: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    apiGet = vi.fn((path: string) => {
      if (path === '/branches') return of({ success: true, data: [] });
      return of({ success: true });
    });
    await TestBed.configureTestingModule({
      imports: [SearchComponent],
      providers: [
        { provide: ApiService, useValue: { get: apiGet } },
        { provide: SearchStateService, useValue: {
            filters: vi.fn().mockReturnValue({ checkIn: '2026-12-20', checkOut: '2026-12-22', branchSlug: '', adults: 2, children: 0 }),
            getApiParams: vi.fn().mockReturnValue({}),
            nights: vi.fn().mockReturnValue(2),
            updateBranch: vi.fn(), updateGuests: vi.fn(), updateRawDates: vi.fn()
        } },
        { provide: CurrencyService, useValue: { formatPrice: vi.fn().mockReturnValue('$ 0 COP') } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SearchComponent);
    component = fixture.componentInstance;
  });

  it('ignora availableSuites con suite null sin lanzar TypeError', () => {
    const validSuite = { id: 's1', name: 'Suite Válida', basePrice: 860000 };
    apiGet.mockImplementation((path: string) => {
      if (path === '/branches') return of({ success: true, data: [] });
      return of({ success: true, data: { availableSuites: [
        { suite: null, totalPrice: 500000, pricePerNight: 250000 },
        { suite: validSuite, totalPrice: 989000, pricePerNight: 494500 }
      ] } });
    });
    expect(() => component.fetchResults()).not.toThrow();
    expect(component.suites().length).toBe(1);
    expect(component.suites()[0].totalPrice).toBe(989000);
    expect(component.suites()[0].pricePerNight).toBe(494500);
    expect(component.loading()).toBe(false);
  });

  it('no muta el objeto suite original al mapear precios', () => {
    const validSuite: any = { id: 's2', name: 'Suite Original', basePrice: 100 };
    apiGet.mockImplementation((path: string) => {
      if (path === '/branches') return of({ success: true, data: [] });
      return of({ success: true, data: { availableSuites: [
        { suite: validSuite, totalPrice: 300, pricePerNight: 150 }
      ] } });
    });
    component.fetchResults();
    expect(validSuite.totalPrice).toBeUndefined();
    expect(validSuite.pricePerNight).toBeUndefined();
    expect(component.suites()[0].totalPrice).toBe(300);
  });
});
