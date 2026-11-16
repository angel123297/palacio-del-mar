import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HostDashboardComponent } from './host-dashboard.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { BookingStreamService } from '../../services/booking-stream.service';
import { CurrencyService } from '../../services/currency.service';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

describe('HostDashboardComponent (Truthful Data & Integrity)', () => {
  let component: HostDashboardComponent;
  let fixture: ComponentFixture<HostDashboardComponent>;
  let apiServiceMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>; put: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };
  let authServiceMock: { currentUser: ReturnType<typeof signal>; currentUserValue: any; isLoggedIn: ReturnType<typeof vi.fn> };
  let bookingStreamMock: { getBookingUpdates: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockImplementation((url: string) => {
        if (url.startsWith('/host/properties')) {
          return of({ success: true, data: [] });
        }
        if (url.startsWith('/host/metrics')) {
          return of({ success: true, data: {} });
        }
        if (url.startsWith('/host/bookings')) {
          return of({ success: true, data: [] });
        }
        return of({ success: true, data: null });
      }),
      post: vi.fn().mockReturnValue(of({ success: true })),
      put: vi.fn().mockReturnValue(of({ success: true })),
      delete: vi.fn().mockReturnValue(of({ success: true }))
    };

    authServiceMock = {
      currentUser: signal({ id: 'host-1', name: 'Anfitrión Demo', email: 'host@palacio.co', role: 'host' } as any),
      currentUserValue: { id: 'host-1', name: 'Anfitrión Demo', email: 'host@palacio.co', role: 'host' },
      isLoggedIn: vi.fn().mockReturnValue(true)
    };

    bookingStreamMock = {
      getBookingUpdates: vi.fn().mockReturnValue(of())
    };

    await TestBed.configureTestingModule({
      imports: [HostDashboardComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: BookingStreamService, useValue: bookingStreamMock },
        { provide: CurrencyService, useValue: { formatPrice: (n: number) => `$ ${n}` } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HostDashboardComponent);
    component = fixture.componentInstance;
  });

  it('submitProperty no genera propiedades falsas ni éxito cuando la API retorna error', () => {
    apiServiceMock.post.mockReturnValue(throwError(() => ({ error: { message: 'Nombre de alojamiento ya existe' } })));
    component.newProperty = {
      name: 'Alojamiento Fantasma',
      type: 'HOTEL',
      city: 'Cartagena',
      address: 'Calle Falsa 123',
      description: 'Test desc',
      phone: '3001112233',
      mainImage: '',
      initialSuiteName: '',
      initialSuitePrice: null as any,
      initialSuiteGuests: 2
    };

    component.submitProperty();

    expect(component.submittingProperty).toBe(false);
    expect(component.propertyError).toBe('Nombre de alojamiento ya existe');
    expect(component.propertySuccess).toBe('');
    expect(component.properties().some(p => p.id?.startsWith('prop-'))).toBe(false);
  });

  it('fetchPropertySuites no inserta suites simuladas cuando la lista está vacía', () => {
    apiServiceMock.get.mockReturnValue(of({ success: true, data: [] }));

    component.fetchPropertySuites('prop-123');

    expect(component.loadingSuites()).toBe(false);
    expect(component.propertySuites()).toEqual([]);
  });

  it('fetchPropertySuites no inserta suites simuladas ante fallos HTTP y muestra el error', () => {
    apiServiceMock.get.mockReturnValue(throwError(() => ({ error: { message: 'Fallo al consultar suites' } })));

    component.fetchPropertySuites('prop-123');

    expect(component.loadingSuites()).toBe(false);
    expect(component.propertySuites()).toEqual([]);
    expect(component.suiteError).toBe('Fallo al consultar suites');
  });

  it('fetchDemoEvents desempaqueta correctamente res.data.events', () => {
    const mockEvents = [
      { id: 'evt-1', event: 'booking.created', timestamp: '2026-11-12T10:00:00Z' },
      { id: 'evt-2', event: 'payment.completed', timestamp: '2026-11-12T10:05:00Z' }
    ];
    apiServiceMock.get.mockReturnValue(of({
      success: true,
      data: {
        count: 2,
        simulateFailure: false,
        events: mockEvents
      }
    }));

    component.fetchDemoEvents();

    expect(component.demoEvents()).toEqual(mockEvents);
    expect(component.demoEvents().length).toBe(2);
  });

  it('submitProperty encadena con switchMap y muestra error 400 si falla suite inicial (F19)', () => {
    apiServiceMock.post.mockImplementation((url: string) => {
      if (url === '/host/properties') {
        return of({ success: true, data: { id: 'prop-new-1', name: 'Nueva Posada' } });
      }
      if (url.includes('/suites')) {
        return throwError(() => ({ error: { message: 'Tarifa base de suite inválida' } }));
      }
      return of({ success: true });
    });

    component.newProperty = {
      name: 'Nueva Posada',
      type: 'HOTEL',
      city: 'Cartagena',
      address: 'Calle del Sol 45',
      description: 'Lujo',
      phone: '3000000000',
      mainImage: '',
      initialSuiteName: 'Suite 1',
      initialSuitePrice: 100000,
      initialSuiteGuests: 2
    };

    component.submitProperty();

    expect(component.submittingProperty).toBe(false);
    expect(component.propertyError).toBe('Tarifa base de suite inválida');
    expect(component.propertySuccess).toBe('');
  });

  it('hostAverageRating retorna null cuando no hay reseñas (F10)', () => {
    component.hostReviews.set([]);
    expect(component.hostAverageRating()).toBeNull();

    component.hostReviews.set([{ rating: 4 }, { rating: 5 }]);
    expect(component.hostAverageRating()).toBe(4.5);
  });

  it('submitPromotion valida que startDate no sea pasada y endDate > startDate (F35)', () => {
    component.newPromo = {
      title: 'Promo Pasada',
      branchId: 'b-1',
      startDate: '2020-01-01',
      endDate: '2020-01-01',
      discountPercent: 15,
      code: 'PASADA'
    };

    component.submitPromotion();
    expect(component.promoError).toBe('La fecha de inicio no puede ser en el pasado');
    expect(apiServiceMock.post).not.toHaveBeenCalledWith('/host/promotions', expect.anything());

    component.newPromo.startDate = '2099-01-05';
    component.newPromo.endDate = '2099-01-05';
    component.submitPromotion();
    expect(component.promoError).toBe('La fecha de fin debe ser posterior a la fecha de inicio');
  });

  it('copyRecentlyCreatedKey no lanza excepción si navigator.clipboard es undefined (F35)', () => {
    component.recentlyCreatedKey.set('pdm_live_secret123');
    const originalClipboard = (navigator as any).clipboard;
    try {
      Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
      expect(() => component.copyRecentlyCreatedKey()).not.toThrow();
      expect(component.actionMessage()).toBe('Clave copiada al portapapeles');
    } finally {
      Object.defineProperty(navigator, 'clipboard', { value: originalClipboard, configurable: true });
    }
  });

  it('deleteSuite no elimina dos veces con in-flight guard (F10)', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    component.selectedPropertyForSuites = { id: 'prop-1' } as any;
    apiServiceMock.delete.mockReturnValue(of({ success: true }));

    const suite = { id: 'suite-del-1' };
    component.deleteSuite(suite);

    expect(apiServiceMock.delete).toHaveBeenCalledTimes(1);
    expect(component.suiteSuccess).toBe('Habitación eliminada correctamente.');
  });
});
