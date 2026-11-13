import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BookingDetailComponent } from './booking-detail.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { of } from 'rxjs';

describe('BookingDetailComponent (Anti-IDOR y Roles)', () => {
  let component: BookingDetailComponent;
  let fixture: ComponentFixture<BookingDetailComponent>;
  let apiServiceMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
  let authServiceMock: { currentUserValue: any };

  const guestBooking = {
    id: 'bk-guest-100',
    _id: 'bk-guest-100',
    bookingCode: 'PDM-TEST-GUEST',
    status: 'checked_out',
    user: 'user-guest-1',
    guestName: 'Carlos Huésped',
    guestEmail: 'carlos@example.com',
    guestPhone: '+57 300 123 4567',
    checkIn: '2026-10-15',
    checkOut: '2026-10-18',
    nights: 3,
    guests: 2,
    children: 1,
    needsCrib: true,
    totalPrice: 900000,
    platformFee: 72000,
    hostPayout: 828000,
    paymentStatus: 'paid',
    guaranteeDeposit: 150000,
    guaranteeRefunded: true,
    suite: { name: 'Suite Imperial' },
    branch: { name: 'Sede Bocagrande' }
  };

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockImplementation((url: string) => {
        if (url.startsWith('/bookings/')) {
          return of({ success: true, data: guestBooking });
        }
        if (url.startsWith('/reviews/booking/')) {
          return of({ success: true, data: null });
        }
        return of({ success: true, data: null });
      }),
      post: vi.fn().mockReturnValue(of({ success: true }))
    };

    authServiceMock = {
      currentUserValue: null
    };

    await TestBed.configureTestingModule({
      imports: [BookingDetailComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'bk-guest-100' })
            }
          }
        }
      ]
    }).compileComponents();
  });

  it('cuando un ANFITRIÓN consulta la reserva de un huésped: oculta Google Maps, oculta el form de calificar y muestra datos del huésped', () => {
    authServiceMock.currentUserValue = {
      id: 'host-owner-99',
      role: 'host',
      name: 'Anfitrión Propietario',
      email: 'host@palacio.co'
    };

    fixture = TestBed.createComponent(BookingDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isGuestOwner).toBe(false);
    expect(component.isHostOrAdmin).toBe(true);
    expect(component.backLink.url).toBe('/anfitrion');
    expect(component.backLink.label).toContain('Panel de Anfitrión');

    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent || '';

    // No debe aparecer la guía turística ni el botón de Google Maps para el anfitrión
    expect(text).not.toContain('¿Cómo llegar al hotel?');
    expect(text).not.toContain('Abrir en Google Maps');
    expect(compiled.querySelector('iframe')).toBeNull();

    // No debe aparecer el botón para calificar la estancia
    expect(text).not.toContain('Publicar Reseña Verificada');
    expect(compiled.querySelector('textarea')).toBeNull();

    // Debe mostrar la información del huésped y liquidación para el anfitrión
    expect(text).toContain('Datos del Huésped');
    expect(text).toContain('Carlos Huésped');
    expect(text).toContain('Liquidación de la Reserva');
    expect(text).toContain('Pago Neto al Anfitrión');
  });

  it('cuando el HUÉSPED TITULAR consulta su reserva: muestra Google Maps y permite publicar reseña verificada', () => {
    authServiceMock.currentUserValue = {
      id: 'user-guest-1',
      role: 'user',
      name: 'Carlos Huésped',
      email: 'carlos@example.com'
    };

    fixture = TestBed.createComponent(BookingDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isGuestOwner).toBe(true);
    expect(component.backLink.url).toBe('/mis-reservas');

    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent || '';

    // Debe mostrar la guía turística y Google Maps para el huésped
    expect(text).toContain('¿Cómo llegar al hotel?');
    expect(text).toContain('Abrir en Google Maps');
    expect(compiled.querySelector('iframe')).not.toBeNull();

    // Debe mostrar el formulario de calificación verificada
    expect(text).toContain('Calificación Verificada de tu Estancia');
    expect(text).toContain('Publicar Reseña Verificada');
    expect(compiled.querySelector('textarea')).not.toBeNull();
  });

  it('cuando un ADMIN consulta la reserva: tiene enlace de regreso a /admin y no ve guía de turista', () => {
    authServiceMock.currentUserValue = {
      id: 'admin-01',
      role: 'admin',
      name: 'Super Admin',
      email: 'admin@palacio.co'
    };

    fixture = TestBed.createComponent(BookingDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.isGuestOwner).toBe(false);
    expect(component.isHostOrAdmin).toBe(true);
    expect(component.backLink.url).toBe('/admin');

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('¿Cómo llegar al hotel?');
    expect(compiled.textContent).not.toContain('Publicar Reseña Verificada');
  });
});
