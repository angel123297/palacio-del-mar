import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExternalHotelComponent } from './external-hotel.component';
import { ApiService } from '../../services/api.service';
import { of } from 'rxjs';

import { provideRouter } from '@angular/router';

describe('ExternalHotelComponent (F36)', () => {
  let component: ExternalHotelComponent;
  let fixture: ComponentFixture<ExternalHotelComponent>;
  let apiServiceMock: any;

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockReturnValue(of({ success: true })),
      post: vi.fn().mockReturnValue(of({ success: true })),
      put: vi.fn().mockReturnValue(of({ success: true, message: 'Updated' }))
    };

    await TestBed.configureTestingModule({
      imports: [ExternalHotelComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ExternalHotelComponent);
    component = fixture.componentInstance;
  });

  it('syncAvailability rechaza peticion si no hay API Key', () => {
    component.apiKey = '';
    component.syncAvailability();

    expect(component.availSuccess()).toBe(false);
    expect(component.availResult()).toContain('Por favor ingresa tu API Key');
    expect(apiServiceMock.put).not.toHaveBeenCalled();
  });

  it('syncAvailability valida que endDate sea posterior a startDate (F36)', () => {
    component.apiKey = 'pdm_live_test_123';
    component.avail = {
      externalId: 'ext-room-1',
      startDate: '2026-12-10',
      endDate: '2026-12-08',
      action: 'BLOCK',
      reason: 'Overbooking prevention'
    };

    component.syncAvailability();

    expect(component.availSuccess()).toBe(false);
    expect(component.availResult()).toContain('La fecha de check-out debe ser posterior a la fecha de check-in');
    expect(apiServiceMock.put).not.toHaveBeenCalled();

    // Mismo día
    component.avail.endDate = '2026-12-10';
    component.syncAvailability();
    expect(component.availSuccess()).toBe(false);
    expect(component.availResult()).toContain('La fecha de check-out debe ser posterior a la fecha de check-in');
    expect(apiServiceMock.put).not.toHaveBeenCalled();
  });

  it('syncAvailability llama a PUT /integration/availability con rango válido', () => {
    component.apiKey = 'pdm_live_test_123';
    component.avail = {
      externalId: 'ext-room-1',
      startDate: '2026-12-10',
      endDate: '2026-12-15',
      action: 'BLOCK',
      reason: 'Overbooking prevention'
    };

    component.syncAvailability();

    expect(component.loadingAvail).toBe(false);
    expect(component.availSuccess()).toBe(true);
    expect(apiServiceMock.put).toHaveBeenCalledWith(
      '/integration/availability',
      component.avail,
      expect.objectContaining({ 'X-Palacio-Api-Key': 'pdm_live_test_123' })
    );
  });
});
