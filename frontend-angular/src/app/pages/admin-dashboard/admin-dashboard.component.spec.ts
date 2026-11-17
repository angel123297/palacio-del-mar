import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { BookingStreamService } from '../../services/booking-stream.service';
import { CurrencyService } from '../../services/currency.service';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';

describe('AdminDashboardComponent (Integrity, Robustness & Modals)', () => {
  let component: AdminDashboardComponent;
  let fixture: ComponentFixture<AdminDashboardComponent>;
  let apiServiceMock: any;
  let authServiceMock: any;
  let bookingStreamMock: any;

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockImplementation((url: string) => {
        if (url.startsWith('/admin/metrics')) return of({ success: true, data: {} });
        if (url.startsWith('/admin/bookings')) return of({ success: true, data: { content: [], totalPages: 1, totalElements: 0 } });
        if (url.startsWith('/admin/promotions')) return of({ success: true, data: [] });
        if (url.startsWith('/admin/properties')) return of({ success: true, data: [] });
        if (url.startsWith('/admin/payments/refunds')) return of({ success: true, data: [] });
        return of({ success: true, data: null });
      }),
      post: vi.fn().mockReturnValue(of({ success: true })),
      put: vi.fn().mockReturnValue(of({ success: true })),
      delete: vi.fn().mockReturnValue(of({ success: true })),
      getBlob: vi.fn().mockReturnValue(of(new Blob(['col1,col2'], { type: 'text/csv' })))
    };

    authServiceMock = {
      currentUser: signal({ id: 'admin-1', name: 'Administrador Demo', email: 'admin@palacio.co', role: 'admin' }),
      currentUserValue: { id: 'admin-1', name: 'Administrador Demo', email: 'admin@palacio.co', role: 'admin' },
      isLoggedIn: vi.fn().mockReturnValue(true)
    };

    bookingStreamMock = {
      getBookingUpdates: vi.fn().mockReturnValue(of())
    };

    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: BookingStreamService, useValue: bookingStreamMock },
        { provide: CurrencyService, useValue: { formatPrice: (n: number) => `$ ${n}` } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDashboardComponent);
    component = fixture.componentInstance;
  });

  it('submitPromotion valida fechas en pasado y endDate <= startDate', () => {
    component.newPromo = {
      title: 'Promo Pasada Admin',
      branchId: 'all',
      startDate: '2021-05-01',
      endDate: '2021-05-01',
      discountPercent: 15,
      code: 'PROMO_PASADA'
    };

    component.submitPromotion();
    expect(component.promoError).toBe('La fecha de inicio no puede ser en el pasado');
    expect(apiServiceMock.post).not.toHaveBeenCalledWith('/admin/promotions', expect.anything());

    component.newPromo.startDate = '2099-10-10';
    component.newPromo.endDate = '2099-10-10';
    component.submitPromotion();
    expect(component.promoError).toBe('La fecha de fin debe ser posterior a la fecha de inicio');
  });

  it('approveBooking previene múltiples envíos simultáneos con processingBookingId guard', () => {
    apiServiceMock.put.mockReturnValue(of({ success: true }));
    const booking = { id: 'bk-adm-1', bookingCode: 'BK-1001' } as any;

    component.approveBooking(booking);
    expect(apiServiceMock.put).toHaveBeenCalledTimes(1);

    // Intentar segundo clic mientras se procesa
    (component as any).processingBookingId.set('bk-adm-1');
    component.approveBooking(booking);
    expect(apiServiceMock.put).toHaveBeenCalledTimes(1);
  });

  it('fetchPromotions y fetchProperties manejan errores HTTP y muestran actionError', () => {
    apiServiceMock.get.mockReturnValue(throwError(() => ({ error: { message: 'Fallo de conexión al cargar promociones' } })));

    component.fetchPromotions();
    expect(component.actionError()).toBe('Fallo de conexión al cargar promociones');

    apiServiceMock.get.mockReturnValue(throwError(() => ({ error: { message: 'Fallo al cargar alojamientos' } })));
    component.fetchProperties();
    expect(component.actionError()).toBe('Fallo al cargar alojamientos');
  });

  it('cierra modales al presionar la tecla Escape vía onEscape', () => {
    component.approvalModalProperty.set({ id: 'prop-1', name: 'Hotel San Diego' } as any);
    component.onEscape();
    expect(component.approvalModalProperty()).toBeNull();

    component.previewProperty.set({ id: 'prop-1', name: 'Hotel San Diego' } as any);
    component.onEscape();
    expect(component.previewProperty()).toBeNull();
  });
});
