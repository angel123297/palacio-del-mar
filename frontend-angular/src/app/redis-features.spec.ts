import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, of } from 'rxjs';
import { SuiteDetailComponent } from './pages/suite-detail/suite-detail.component';
import { CheckoutComponent } from './pages/checkout/checkout';
import { ProfileComponent } from './pages/profile/profile';
import { AuthService } from './services/auth.service';

describe('Redis Pillars Frontend Integration (Social Proof, Geo, Idempotency & Global Logout)', () => {
  describe('SuiteDetailComponent — Social Proof, Geo Cartagena & Booking Idempotency', () => {
    let fixture: ComponentFixture<SuiteDetailComponent>;
    let component: SuiteDetailComponent;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [SuiteDetailComponent],
        providers: [
          provideRouter([{ path: '**', component: ProfileComponent }]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: {
                paramMap: convertToParamMap({ idOrSlug: 'penthouse-bocagrande' })
              }
            }
          }
        ]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      localStorage.setItem('token', 'mock-jwt-token');
      localStorage.setItem('user', JSON.stringify({
        id: 'guest-1',
        name: 'Laura Viajera',
        email: 'laura@palaciodelmar.co',
        role: 'user',
        emailVerified: true
      }));
      const auth = TestBed.inject(AuthService);
      auth.loadCachedUser();

      fixture = TestBed.createComponent(SuiteDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    afterEach(() => {
      fixture.destroy();
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      // Drain any background requests (e.g. date picker monthly availability)
      httpMock.match(() => true);
      httpMock.verify();
    });

    it('envía ping con X-Viewer-Session y renderiza el banner de Social Proof y lugares Geo cercanos', () => {
      const suiteReq = httpMock.expectOne((r) => r.url.endsWith('/api/suites/penthouse-bocagrande') && r.method === 'GET');
      suiteReq.flush({
        data: {
          id: 'suite-1',
          slug: 'penthouse-bocagrande',
          name: 'Penthouse Ocean Front',
          description: 'Vista al mar Caribe',
          basePrice: 1200000,
          maxGuests: 4,
          propertyId: 'prop-1'
        }
      });

      const expReq = httpMock.expectOne((r) => r.url.endsWith('/api/experiences') && r.method === 'GET');
      expReq.flush({ data: [] });

      const pingReq = httpMock.expectOne((r) => r.url.endsWith('/api/suites/penthouse-bocagrande/viewers/ping') && r.method === 'POST');
      expect(pingReq.request.headers.get('X-Viewer-Session')).toMatch(/^vw_/);
      pingReq.flush({
        data: {
          suiteId: 'penthouse-bocagrande',
          activeViewers: 4,
          bookingsLast24h: 2,
          lastBookedMinutesAgo: 18,
          highDemand: true
        }
      });

      const geoReq = httpMock.expectOne((r) => r.url.includes('/api/geo/suites/penthouse-bocagrande/nearby') && r.method === 'GET');
      geoReq.flush({
        data: [
          { id: 'torre-del-reloj', name: 'Torre del Reloj', zone: 'Centro Histórico', distanceKm: 2.4, walkMinutes: null, driveMinutes: 6 },
          { id: 'playa-bocagrande', name: 'Playas de Bocagrande', zone: 'Bocagrande', distanceKm: 0.3, walkMinutes: 4, driveMinutes: 1 }
        ]
      });

      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;

      const socialBanner = el.querySelector('.social-proof-banner[aria-live="polite"]');
      expect(socialBanner).toBeTruthy();
      expect(socialBanner?.textContent).toContain('4 viajeros están viendo esta suite');
      expect(socialBanner?.textContent).toContain('Alta demanda');

      const geoCards = el.querySelectorAll('.geo-place-card');
      expect(geoCards.length).toBe(2);
      expect(el.textContent).toContain('Torre del Reloj');
      expect(el.textContent).toContain('Playas de Bocagrande');
    });

    it('envía cabecera Idempotency-Key en POST /bookings y limpia el intervalo de ping en ngOnDestroy', () => {
      // Flush initial requests
      httpMock.match((r) => r.url.includes('/api/suites/penthouse-bocagrande'))[0]?.flush({
        data: { id: 'suite-1', slug: 'penthouse-bocagrande', name: 'Penthouse', basePrice: 900000, maxGuests: 4 }
      });
      httpMock.match((r) => r.url.includes('/api/experiences'))[0]?.flush({ data: [] });
      httpMock.match((r) => r.url.includes('/viewers/ping'))[0]?.flush({ data: { activeViewers: 1, bookingsLast24h: 0, highDemand: false } });
      httpMock.match((r) => r.url.includes('/api/geo/'))[0]?.flush({ data: [] });

      component.suite = { id: 'suite-1', slug: 'penthouse-bocagrande', name: 'Penthouse', basePrice: 900000, maxGuests: 4 } as any;
      component.guestName = 'Laura Viajera';
      component.guestEmail = 'laura@palaciodelmar.co';
      component.guestPhone = '3001234567';

      // Invoke private submitBooking directly to validate Idempotency-Key header determinism
      (component as any).submitBooking();
      const firstBookReq = httpMock.expectOne((r) => r.url.endsWith('/api/bookings') && r.method === 'POST');
      const firstKey = firstBookReq.request.headers.get('Idempotency-Key');
      expect(firstKey).toBeTruthy();
      expect(firstKey).toMatch(/^bk_/);
      firstBookReq.flush({ message: 'Red temporal' }, { status: 503, statusText: 'Service Unavailable' });

      // Retry with identical booking parameters -> must reuse exact same Idempotency-Key
      (component as any).submitBooking();
      const retryBookReq = httpMock.expectOne((r) => r.url.endsWith('/api/bookings') && r.method === 'POST');
      expect(retryBookReq.request.headers.get('Idempotency-Key')).toBe(firstKey);
      retryBookReq.flush({ data: { id: 'bk-123' } });

      expect((component as any).viewerPingTimer).not.toBeNull();
      component.ngOnDestroy();
      expect((component as any).viewerPingTimer).toBeNull();
    });
  });

  describe('CheckoutComponent — Payment Checkout & Confirm Idempotency Keys', () => {
    let fixture: ComponentFixture<CheckoutComponent>;
    let component: CheckoutComponent;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
      const paramMap$ = new BehaviorSubject(convertToParamMap({ bookingId: 'bk-pay-1' }));
      await TestBed.configureTestingModule({
        imports: [CheckoutComponent],
        providers: [
          provideRouter([{ path: '**', component: ProfileComponent }]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: ActivatedRoute,
            useValue: { paramMap: paramMap$.asObservable() }
          }
        ]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(CheckoutComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    afterEach(() => {
      fixture.destroy();
      httpMock.match(() => true);
      httpMock.verify();
    });

    it('adjunta Idempotency-Key tanto en POST /payments/checkout como en POST /payments/confirm y rota tras rechazo 4xx', () => {
      const loadReq = httpMock.expectOne((r) => r.url.endsWith('/api/bookings/bk-pay-1') && r.method === 'GET');
      loadReq.flush({
        data: {
          id: 'bk-pay-1',
          bookingCode: 'PDM-20261026-PAY1',
          totalPrice: 1500000,
          paidAmount: 0,
          balanceDue: 1500000,
          paymentStatus: 'pending',
          checkIn: '2026-12-01',
          checkOut: '2026-12-03'
        }
      });

      (component as any).executePaymentPayload({
        method: 'CREDIT_CARD',
        cardNumber: '4242424242420002'
      });

      const checkoutReq1 = httpMock.expectOne((r) => r.url.endsWith('/api/payments/checkout') && r.method === 'POST');
      const chkKey1 = checkoutReq1.request.headers.get('Idempotency-Key');
      expect(chkKey1).toMatch(/^chk_/);
      checkoutReq1.flush({ data: { intentId: 'pi_test_1' } });

      const confirmReq1 = httpMock.expectOne((r) => r.url.endsWith('/api/payments/confirm') && r.method === 'POST');
      const cfmKey1 = confirmReq1.request.headers.get('Idempotency-Key');
      expect(cfmKey1).toMatch(/^cfm_/);
      // Simulate 400 card decline -> component must clear fingerprint so next attempt gets fresh keys
      confirmReq1.flush({ message: 'Fondos insuficientes' }, { status: 400, statusText: 'Bad Request' });

      (component as any).executePaymentPayload({
        method: 'CREDIT_CARD',
        cardNumber: '4242424242420002'
      });

      const checkoutReq2 = httpMock.expectOne((r) => r.url.endsWith('/api/payments/checkout') && r.method === 'POST');
      const chkKey2 = checkoutReq2.request.headers.get('Idempotency-Key');
      expect(chkKey2).toMatch(/^chk_/);
      expect(chkKey2).not.toBe(chkKey1);
      checkoutReq2.flush({ data: { intentId: 'pi_test_2' } });

      const confirmReq2 = httpMock.expectOne((r) => r.url.endsWith('/api/payments/confirm') && r.method === 'POST');
      expect(confirmReq2.request.headers.get('Idempotency-Key')).not.toBe(cfmKey1);
      confirmReq2.flush({ data: { status: 'completed', transactionId: 'tx_ok_2' } });
    });
  });

  describe('ProfileComponent — Global Device Logout (POST /auth/logout-all)', () => {
    let fixture: ComponentFixture<ProfileComponent>;
    let httpMock: HttpTestingController;
    let authService: AuthService;

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [ProfileComponent],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      localStorage.setItem('token', 'token-active-session');
      localStorage.setItem('user', JSON.stringify({
        id: 'u-sec-1',
        name: 'Carlos Seguridad',
        email: 'carlos@palaciodelmar.co',
        role: 'user',
        emailVerified: true
      }));
      authService = TestBed.inject(AuthService);
      authService.loadCachedUser();

      fixture = TestBed.createComponent(ProfileComponent);
      fixture.detectChanges();
    });

    afterEach(() => {
      fixture.destroy();
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      httpMock.match(() => true);
      httpMock.verify();
    });

    it('invoca POST /auth/logout-all al pulsar #btn-logout-all-devices y limpia la sesión local', () => {
      const meReqs = httpMock.match((r) => r.url.endsWith('/api/auth/me') && r.method === 'GET');
      meReqs.forEach((req) =>
        req.flush({ user: { id: 'u-sec-1', name: 'Carlos Seguridad', email: 'carlos@palaciodelmar.co', emailVerified: true } })
      );
      fixture.detectChanges();

      const btn = (fixture.nativeElement as HTMLElement).querySelector('#btn-logout-all-devices') as HTMLButtonElement;
      expect(btn).toBeTruthy();

      btn.click();

      const logoutAllReq = httpMock.expectOne((r) => r.url.endsWith('/api/auth/logout-all') && r.method === 'POST');
      logoutAllReq.flush({ status: 'success', message: 'Sesiones cerradas en todos los dispositivos.' });

      expect(authService.currentUser()).toBeNull();
      expect(localStorage.getItem('token')).toBeNull();
    });
  });
});
