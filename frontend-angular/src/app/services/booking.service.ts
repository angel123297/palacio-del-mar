import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Booking } from '../models/types';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private api: ApiService) {}
  /** GET /bookings devuelve solo las reservas del usuario autenticado (filtro del backend). */
  getMyBookings(): Observable<Booking[]> {
    return this.api.get<any>('/bookings').pipe(
      map(res => (res?.data || res) as Booking[])
    );
  }
  createBooking(booking: any, idempotencyKey?: string): Observable<Booking> {
    const key = idempotencyKey || `bk_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
    return this.api.post<any>('/bookings', booking, { 'Idempotency-Key': key }).pipe(
      map(res => (res?.data || res) as Booking)
    );
  }
}
