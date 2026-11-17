import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { BookingStreamService } from './booking-stream.service';
import { AuthService } from './auth.service';
import { User, Booking } from '../models/types';

describe('BookingStreamService (F1 & N24)', () => {
  let service: BookingStreamService;
  let mockCurrentUser = signal<User | null>(null);
  let mockAuthService: any;
  let listeners: { [event: string]: ((e: any) => void)[] } = {};
  let lastEventSourceInstance: any = null;

  class MockEventSource {
    url: string;
    options: any;
    onopen: any = null;
    onerror: any = null;
    onmessage: any = null;
    closed = false;

    constructor(url: string, options?: any) {
      this.url = url;
      this.options = options;
      lastEventSourceInstance = this;
    }

    addEventListener(event: string, callback: any) {
      if (!listeners[event]) {
        listeners[event] = [];
      }
      listeners[event].push(callback);
    }

    close() {
      this.closed = true;
    }
  }

  beforeEach(() => {
    listeners = {};
    lastEventSourceInstance = null;
    (globalThis as any).EventSource = MockEventSource;

    mockCurrentUser.set({ id: 'u1', email: 'guest@test.com', role: 'user', name: 'Guest' } as User);
    mockAuthService = {
      currentUser: mockCurrentUser,
      isLoggedIn: vi.fn(() => !!mockCurrentUser())
    };

    TestBed.configureTestingModule({
      providers: [
        BookingStreamService,
        { provide: AuthService, useValue: mockAuthService }
      ]
    });

    service = TestBed.inject(BookingStreamService);
  });

  afterEach(() => {
    service.closeConnection();
  });

  it('connects using withCredentials: true and without token in query param (F1)', () => {
    const sub = service.getBookingUpdates().subscribe();
    expect(lastEventSourceInstance).toBeTruthy();
    expect(lastEventSourceInstance.url).not.toContain('?token=');
    expect(lastEventSourceInstance.url).not.toContain('&token=');
    expect(lastEventSourceInstance.options?.withCredentials).toBe(true);
    sub.unsubscribe();
  });

  it('receives and emits booking_created and other lifecycle events (N24)', () => {
    let received: Booking | null = null;
    const sub = service.getBookingUpdates().subscribe(b => {
      received = b;
    });

    const mockBooking: Booking = {
      id: 'b-123',
      bookingCode: 'PDM-123',
      suiteId: 's-1',
      checkIn: '2026-12-01',
      checkOut: '2026-12-05',
      totalPrice: 1500000,
      status: 'pending'
    } as any;

    expect(listeners['booking_created']).toBeDefined();
    expect(listeners['booking_created'].length).toBeGreaterThan(0);

    // Trigger booking_created
    listeners['booking_created'][0]({ data: JSON.stringify(mockBooking) });
    expect(received).toEqual(mockBooking);

    // Trigger booking_confirmed
    const confirmedBooking = { ...mockBooking, status: 'confirmed' as const };
    listeners['booking_confirmed'][0]({ data: JSON.stringify(confirmedBooking) });
    expect(received).toEqual(confirmedBooking);

    sub.unsubscribe();
  });
});
