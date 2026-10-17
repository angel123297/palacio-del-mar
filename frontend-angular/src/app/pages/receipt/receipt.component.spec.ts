import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReceiptComponent } from './receipt.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { of } from 'rxjs';

describe('ReceiptComponent', () => {
  let component: ReceiptComponent;
  let fixture: ComponentFixture<ReceiptComponent>;
  let apiServiceMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };

  const mockBooking = {
    id: 'booking-789',
    bookingCode: 'PDM-TEST-001',
    status: 'checked_out',
    smartLockPin: '8492',
    checkIn: '2026-11-01T15:00:00Z',
    checkOut: '2026-11-05T13:00:00Z',
    totalPrice: 450000,
    suite: { name: 'Suite Presidencial Mar' },
    guestDetails: { fullName: 'Juan Perez', email: 'juan@example.com' }
  };

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockReturnValue(of({ success: true, data: mockBooking })),
      post: vi.fn().mockReturnValue(of({ success: true }))
    };

    await TestBed.configureTestingModule({
      imports: [ReceiptComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'booking-789' })
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ReceiptComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse e inicializar el comprobante con la reserva', () => {
    expect(component).toBeTruthy();
    expect(component.booking).toBeDefined();
    expect(component.bookingCodeDisplay).toBe('PDM-TEST-001');
  });

  it('debe mostrar el PIN de cerradura inteligente correctamente', () => {
    expect(component.smartLockPinDisplay).toBe('8492');
  });

  it('debe generar el código QR SVG offline con el payload de seguridad', () => {
    component.generateQr();
    expect(component.qrSvgSafe).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    const qrContainer = compiled.querySelector('.access-pass');
    expect(qrContainer).toBeTruthy();
  });

  it('debe permitir calificar la estadía cuando el estado es checked_out', () => {
    expect(component.canLeaveReview).toBe(true);
  });

  it('debe mostrar "Pendiente de asignación" cuando smartLockPin no está asignado', () => {
    component.booking = { ...mockBooking, smartLockPin: undefined as any };
    expect(component.smartLockPinDisplay).toBe('Pendiente de asignación');
  });
});
