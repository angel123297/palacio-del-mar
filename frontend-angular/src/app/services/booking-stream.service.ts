import { Injectable, NgZone } from '@angular/core';
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

  constructor(
    private auth: AuthService,
    private zone: NgZone
  ) {}

  public getBookingUpdates(): Observable<Booking> {
    this.ensureConnection();
    return this.bookingUpdates$.asObservable();
  }

  private ensureConnection(): void {
    if (this.eventSource || !this.auth.isLoggedIn()) {
      return;
    }

    const token = typeof localStorage !== 'undefined' ? localStorage.getItem('token') : null;
    if (!token) return;

    try {
      // Endpoint SSE autenticado via query token o cookie según soporte del proxy
      const sseUrl = `${environment.apiUrl || 'http://localhost:5000/api'}/bookings/stream?token=${encodeURIComponent(token)}`;
      this.eventSource = new EventSource(sseUrl);

      this.eventSource.addEventListener('booking_confirmed', (event: MessageEvent) => {
        this.zone.run(() => {
          try {
            const data: Booking = JSON.parse(event.data);
            this.bookingUpdates$.next(data);
          } catch (e) {
            console.warn('[BookingStreamService] Error parseando payload de reserva:', e);
          }
        });
      });

      this.eventSource.onerror = () => {
        this.closeConnection();
        // Reintento exponencial
        if (!this.reconnectTimeout && this.auth.isLoggedIn()) {
          this.reconnectTimeout = setTimeout(() => {
            this.reconnectTimeout = null;
            this.ensureConnection();
          }, 5000);
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
