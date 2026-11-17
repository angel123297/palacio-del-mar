import { Injectable, NgZone, effect } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { Booking } from '../models/types';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class BookingStreamService {
  private bookingUpdates$ = new Subject<Booking>();
  private eventSource: EventSource | null = null;
  private reconnectTimeout: any = null;
  private retryAttempts = 0;
  private readonly maxRetries = 5;
  private hasActiveSubscribers = false;

  constructor(
    private auth: AuthService,
    private zone: NgZone
  ) {
    effect(() => {
      const user = this.auth.currentUser();
      if (!user) {
        this.closeConnection();
        this.retryAttempts = 0;
      } else if (this.hasActiveSubscribers) {
        this.ensureConnection();
      }
    });
  }

  public getBookingUpdates(): Observable<Booking> {
    this.hasActiveSubscribers = true;
    this.ensureConnection();
    return this.bookingUpdates$.asObservable();
  }

  private ensureConnection(): void {
    if (this.eventSource || !this.auth.isLoggedIn()) {
      return;
    }

    try {
      const sseUrl = `${environment.apiUrl || 'http://localhost:5000/api'}/bookings/stream`;
      this.eventSource = new EventSource(sseUrl, { withCredentials: true });

      this.eventSource.onopen = () => {
        this.retryAttempts = 0;
      };

      const handleBookingEvent = (event: MessageEvent) => {
        this.retryAttempts = 0;
        this.zone.run(() => {
          try {
            const data: Booking = JSON.parse(event.data);
            this.bookingUpdates$.next(data);
          } catch (e) {
            console.warn('[BookingStreamService] Error parseando payload de reserva:', e);
          }
        });
      };

      const eventNames = [
        'booking_confirmed',
        'booking_created',
        'booking_cancelled',
        'booking_checked_in',
        'booking_checked_out',
        'booking_refunded',
        'booking_expired',
        'booking_modified',
        'booking_updated',
        'booking_status_updated'
      ];
      eventNames.forEach(name => this.eventSource?.addEventListener(name, handleBookingEvent));
      this.eventSource.onmessage = handleBookingEvent;

      this.eventSource.onerror = () => {
        this.closeConnection();
        // Reintento exponencial con tope y máximo de intentos
        if (!this.reconnectTimeout && this.auth.isLoggedIn() && this.retryAttempts < this.maxRetries) {
          const delay = Math.min(1000 * Math.pow(2, this.retryAttempts), 30000);
          this.retryAttempts++;
          this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this.ensureConnection();
          }, delay);
        }
      };
    } catch (err) {
      console.warn('[BookingStreamService] No se pudo inicializar EventSource:', err);
    }
  }

  public closeConnection(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  }
}
