import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { ReceiptComponent } from './receipt.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
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
          provide: AuthService,
          useValue: { currentUserValue: { id: 'guest-1', name: 'Juan Perez', email: 'juan@example.com', role: 'user' } }
        },
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

  it('debe eliminar caracteres peligrosos del código de reserva y PIN antes de generar el QR', () => {
    component.booking = {
      ...mockBooking,
      bookingCode: '<img src=x onerror=alert(1)>AB #1',
      smartLockPin: '12<script>alert(1)</script>#34'
    };

    component.generateQr();
    const sanitizer = TestBed.inject(DomSanitizer);
    const generatedFromUntrustedValues = sanitizer.sanitize(SecurityContext.HTML, component.qrSvgSafe);

    component.booking = {
      ...mockBooking,
      bookingCode: 'imgsrcxonerroralert1AB1',
      smartLockPin: '12scriptalert1script34'
    };
    component.generateQr();

    expect(sanitizer.sanitize(SecurityContext.HTML, component.qrSvgSafe)).toBe(
      generatedFromUntrustedValues
    );
  });

  it('debe permitir calificar la estadía cuando el estado es checked_out', () => {
    expect(component.canLeaveReview).toBe(true);
  });

  it('no debe permitir calificar si el estado no es checked_out o completed', () => {
    component.booking = { ...mockBooking, status: 'confirmed' };
    expect(component.canLeaveReview).toBe(false);
  });

  it('debe mostrar el banner de reseña verificada cuando canLeaveReview es true', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Tu Opinión como Huésped Verificado');
  });

  it('debe mostrar "Pendiente de asignación" cuando smartLockPin no está asignado', () => {
    component.booking = { ...mockBooking, smartLockPin: undefined as any };
    expect(component.smartLockPinDisplay).toBe('Pendiente de asignación');
  });
});
