import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DateRangePickerComponent } from './date-range-picker.component';
import { ApiService } from '../../services/api.service';

describe('DateRangePickerComponent', () => {
  let component: DateRangePickerComponent;
  let fixture: ComponentFixture<DateRangePickerComponent>;
  let mockApiService: any;

  beforeEach(async () => {
    mockApiService = {
      get: vi.fn((url: string, params: any) => {
        if (url.includes('/monthly')) {
          return of({
            success: true,
            data: {
              year: 2026,
              month: 11,
              calendar: [
                { date: '2026-11-10', availableSuites: 0, totalSuites: 0, soldOut: true }, // Edge case: totalSuites=0 should NOT be marked sold out
                { date: '2026-11-15', availableSuites: 0, totalSuites: 1, soldOut: true }, // Real sold out: totalSuites=1
                { date: '2026-11-16', availableSuites: 1, totalSuites: 1, soldOut: false }
              ]
            }
          });
        }
        if (url.includes('/peak-dates')) {
          return of({
            success: true,
            data: {
              highSeason: [{ startDate: '2026-12-01', endDate: '2026-12-15', label: 'Temporada Alta' }],
              holidays: [{ date: '2026-11-11', name: 'Independencia de Cartagena' }]
            }
          });
        }
        if (url.includes('/promotions')) {
          return of({ success: true, data: [] });
        }
        return of({ success: true, data: {} });
      })
    };

    await TestBed.configureTestingModule({
      imports: [DateRangePickerComponent],
      providers: [
        { provide: ApiService, useValue: mockApiService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DateRangePickerComponent);
    component = fixture.componentInstance;
    component.today = '2026-11-01';
    component.view.set({ year: 2026, month: 11 });
    component.open.set(true);
    await component.loadMonth(2026, 11);
    await component.loadPeak(2026);
    fixture.detectChanges();
  });

  it('should create and load monthly availability without marking totalSuites=0 as sold out', () => {
    expect(component).toBeTruthy();
    const soldOutMap = component.soldOut();

    // 2026-11-10 had totalSuites=0 and soldOut=true from backend bug; frontend guard must ignore it
    expect(soldOutMap['2026-11-10']).toBeFalsy();

    // 2026-11-15 had totalSuites=1 and availableSuites=0; must be marked sold out
    expect(soldOutMap['2026-11-15']).toBe(true);

    // 2026-11-16 is available
    expect(soldOutMap['2026-11-16']).toBeFalsy();
  });

  it('should parse peak ranges and holidays with contract normalization', () => {
    expect(component.peakRanges().length).toBeGreaterThan(0);
    const peak = component.peakRanges()[0];
    expect(peak.start).toBe('2026-12-01');
    expect(peak.end).toBe('2026-12-15');
    expect(peak.name).toBe('Temporada Alta');

    expect(component.holidays().length).toBeGreaterThan(0);
    const holiday = component.holidays()[0];
    expect(holiday.date).toBe('2026-11-11');
    expect(holiday.name).toBe('Independencia de Cartagena');
  });

  it('should allow checkout on a day whose evening is occupied (hotel turnover)', () => {
    component.checkIn = '2026-11-14';
    component.step.set('out');
    // Night of 2026-11-15 is sold out. CheckIn on 14, checkOut on 15 stays only night 14.
    expect(component.canCheckOut('2026-11-14', '2026-11-15')).toBe(true);
    expect(component.isTurnoverDate('2026-11-15')).toBe(true);
    expect(component.isDayDisabled('2026-11-15')).toBe(false);

    const title = component.dayTitle('2026-11-15', true);
    expect(title).toContain('Disponible para Salida');

    let emitted: any = null;
    component.rangeChange.subscribe((val) => { emitted = val; });

    // Picking 2026-11-15 as check-out should succeed even if night of 15 is sold out
    component.pick('2026-11-15');
    expect(emitted).toEqual({ checkIn: '2026-11-14', checkOut: '2026-11-15' });
  });

  it('should block checkout if an intermediate night is sold out', () => {
    component.checkIn = '2026-11-14';
    component.step.set('out');
    // Night of 15 is sold out, so staying 14 to 16 requires night of 14 and night of 15.
    expect(component.canCheckOut('2026-11-14', '2026-11-16')).toBe(false);
    expect(component.isDayDisabled('2026-11-16')).toBe(true);
    expect(component.isTurnoverDate('2026-11-16')).toBe(false);
  });
});
