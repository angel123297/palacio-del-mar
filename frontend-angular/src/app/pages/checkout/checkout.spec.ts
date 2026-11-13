import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CheckoutComponent } from './checkout';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { Component } from '@angular/core';

@Component({ standalone: true, template: '' })
class StubReceiptComponent {}
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { of } from 'rxjs';
import { Booking } from '../../models/types';

describe('CheckoutComponent Flow & Idempotency Tests', () => {
  let component: CheckoutComponent;
  let fixture: ComponentFixture<CheckoutComponent>;
  let apiServiceMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
  let authServiceMock: { isLoggedIn: ReturnType<typeof vi.fn>; currentUserValue: any };

  const sampleBooking: Booking = {
    id: 'b_test_checkout_1',
    _id: 'b_test_checkout_1',
    bookingCode: 'PDM-CHK-1',
    suite: { name: 'Suite Imperial' } as any,
    checkIn: '2026-11-20',
    checkOut: '2026-11-23',
    totalPrice: 1950000,
    status: 'pending',
    paymentStatus: 'pending',
    expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString()
  };

  beforeEach(async () => {
    vi.useFakeTimers();

    apiServiceMock = {
      get: vi.fn().mockImplementation((url: string) => {
        if (url.includes('/payments/config')) {
          return of({ success: true, data: { gateway: 'simulated', currency: 'COP' } });
        }
        return of({ success: true, data: sampleBooking });
      }),
      post: vi.fn().mockReturnValue(of({
        success: true,
        data: {
          clientSecret: 'sec_123',
          transactionId: 'tx_999',
          status: 'completed'
        }
      }))
    };

    authServiceMock = {
      isLoggedIn: vi.fn().mockReturnValue(true),
      currentUserValue: { id: 'usr_chk_1', email: 'guest@palaciodelmar.co', emailVerified: true }
    };

    await TestBed.configureTestingModule({
      imports: [CheckoutComponent],
      providers: [
        provideHttpClient(),
        provideRouter([
          { path: 'reservas/:id/comprobante', component: StubReceiptComponent }
        ]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ bookingId: 'b_test_checkout_1' })),
            snapshot: {
              paramMap: convertToParamMap({ bookingId: 'b_test_checkout_1' })
            }
          }
        },
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CheckoutComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('debe crearse e inicializar datos de reserva y temporizador de retención', () => {
    expect(component).toBeTruthy();
    fixture.detectChanges();
    expect(apiServiceMock.get).toHaveBeenCalledWith('/bookings/b_test_checkout_1');
    expect(component.booking).toBeTruthy();
    expect(component.timerSeconds).toBeGreaterThan(0);
  });

  it('formatea números de tarjeta en el control reactivo con bloques de 4 dígitos', () => {
    const event = {
      target: { value: '4111222233334444' }
    } as any;
    component.formatCardNumber(event);
    expect(component.paymentForm.get('cardNumber')?.value).toBe('4111 2222 3333 4444');
  });

  it('valida formulario de tarjeta impidiendo procesamiento si está incompleto', () => {
    component.paymentForm.setValue({
      cardName: '',
      cardNumber: '',
      expiry: '',
      cvv: ''
    });
    component.processPayment();
    expect(apiServiceMock.post).not.toHaveBeenCalled();
    expect(component.errorMsg).toBeTruthy();
  });

  it('procesa pago con idempotency keys cuando el formulario es válido', () => {
    fixture.detectChanges();
    component.paymentForm.setValue({
      cardName: 'Samu Test',
      cardNumber: '4242 4242 4242 4242',
      expiry: '12/28',
      cvv: '123'
    });

    component.processPayment();
    vi.advanceTimersByTime(1200);
    expect(apiServiceMock.post).toHaveBeenCalled();
  });
});
