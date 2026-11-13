import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MyBookingsComponent } from './my-bookings.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { of, throwError } from 'rxjs';
import { Booking } from '../../models/types';

describe('MyBookingsComponent Logic and Template States', () => {
  let component: MyBookingsComponent;
  let fixture: ComponentFixture<MyBookingsComponent>;
  let apiServiceMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };
  let authServiceMock: { isLoggedIn: ReturnType<typeof vi.fn>; currentUser: ReturnType<typeof vi.fn> };

  const sampleBookings: Booking[] = [
    {
      id: 'b_confirmed_1',
      bookingCode: 'PDM-CONF-1',
      suite: { name: 'Suite Presidencial' } as any,
      checkIn: '2026-11-20',
      checkOut: '2026-11-23',
      totalPrice: 2500000,
      status: 'confirmed',
      paymentStatus: 'paid'
    },
    {
      id: 'b_pending_1',
      bookingCode: 'PDM-PEND-1',
      suite: { name: 'Suite Frente al Mar' } as any,
      checkIn: '2026-11-25',
      checkOut: '2026-11-28',
      totalPrice: 1800000,
      status: 'pending',
      paymentStatus: 'pending',
      expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString()
    }
  ];

  beforeEach(async () => {
    apiServiceMock = {
      get: vi.fn().mockReturnValue(of({ success: true, data: sampleBookings })),
      post: vi.fn().mockReturnValue(of({ success: true }))
    };

    authServiceMock = {
      isLoggedIn: vi.fn().mockReturnValue(true),
      currentUser: vi.fn().mockReturnValue({ id: 'usr_1', email: 'guest@palaciodelmar.co', role: 'user', emailVerified: true })
    };

    await TestBed.configureTestingModule({
      imports: [MyBookingsComponent],
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: ApiService, useValue: apiServiceMock },
        { provide: AuthService, useValue: authServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MyBookingsComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente e inicializar las reservas del usuario', () => {
    expect(component).toBeTruthy();
    fixture.detectChanges();
    expect(apiServiceMock.get).toHaveBeenCalledWith('/bookings');
    expect(component.bookings.length).toBe(2);
    expect(component.loading).toBe(false);
  });

  it('debe devolver la etiqueta en español adecuada según el estado de la reserva', () => {
    expect(component.getStatusLabel('confirmed')).toBe('Confirmada');
    expect(component.getStatusLabel('pending')).toBe('Pendiente de pago');
    expect(component.getStatusLabel('cancelled')).toBe('Cancelada');
    expect(component.getStatusLabel('completed')).toBe('Completada');
    expect(component.getStatusLabel('unknown_state')).toBe('unknown_state');
  });

  it('debe evaluar correctamente si una reserva está expirada o no', () => {
    const expiredBooking: Booking = {
      id: 'b_exp',
      status: 'pending',
      expiresAt: new Date(Date.now() - 5000).toISOString()
    } as any;
    expect(component.isExpired(expiredBooking)).toBe(true);

    const activeBooking: Booking = {
      id: 'b_act',
      status: 'pending',
      expiresAt: new Date(Date.now() + 50000).toISOString()
    } as any;
    expect(component.isExpired(activeBooking)).toBe(false);
  });

  it('maneja errores en la carga de reservas sin romper el componente', () => {
    apiServiceMock.get.mockReturnValue(throwError(() => new Error('Error al conectar con backend')));
    component.fetchMyBookings();
    expect(component.loading).toBe(false);
    expect(component.bookings.length).toBe(0);
  });
});
