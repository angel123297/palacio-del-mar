import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Booking } from '../models/types';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BookingService {
  constructor(private api: ApiService) {}
  getMyBookings(): Observable<Booking[]> { return this.api.get<Booking[]>('/bookings/my-bookings'); }
  createBooking(booking: any): Observable<Booking> { return this.api.post<Booking>('/bookings', booking); }
}
