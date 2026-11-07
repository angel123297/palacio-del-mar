import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { SearchStateService } from './search-state.service';

describe('SearchStateService', () => {
  let service: SearchStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SearchStateService]
    });
    service = TestBed.inject(SearchStateService);
  });

  it('should be created with valid default filters', () => {
    expect(service).toBeTruthy();
    const filters = service.filters();
    expect(filters.adults).toBe(2);
    expect(filters.children).toBe(0);
    expect(filters.checkIn).toBeTruthy();
    expect(filters.checkOut).toBeTruthy();
  });

  it('should accurately calculate nights between checkIn and checkOut', () => {
    service.updateRawDates('2026-11-01', '2026-11-05');
    expect(service.nights()).toBe(4);

    service.updateRawDates('2026-12-10', '2026-12-17');
    expect(service.nights()).toBe(7);
  });

  it('should update guests and vibe and format API parameters properly', () => {
    service.updateGuests(3, 1);
    service.updateVibe('romantico');
    service.updateBranch('palacio-cartagena');
    
    const params = service.getApiParams();
    expect(params['guests']).toBe(4);
    expect(params['branch']).toBe('palacio-cartagena');
    expect(params['vibe']).toBe('romantico');
  });
});
