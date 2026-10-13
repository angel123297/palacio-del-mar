import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Booking } from '../models/types';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private api: ApiService) {}
  getMyBookings(): Observable<Booking[]> {
    return this.api.get<any>('/bookings/my-bookings').pipe(
      map(res => (res?.data || res) as Booking[])
    );
  }
  createBooking(booking: any): Observable<Booking> {
    return this.api.post<any>('/bookings', booking).pipe(
      map(res => (res?.data || res) as Booking)
    );
  }
}
