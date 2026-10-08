#!/bin/bash
cd /home/angel19/Notas/palacio-del-mar/frontend-angular

# Services
cat << 'BRANCH_SRV' > src/app/services/branch.service.ts
import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Branch } from '../models/types';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BranchService {
  constructor(private api: ApiService) {}
  getBranches(): Observable<Branch[]> { return this.api.get<Branch[]>('/branches'); }
}
BRANCH_SRV

cat << 'EXP_SRV' > src/app/services/experience.service.ts
import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Experience } from '../models/types';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExperienceService {
  constructor(private api: ApiService) {}
  getExperiences(): Observable<Experience[]> { return this.api.get<Experience[]>('/experiences'); }
}
EXP_SRV

cat << 'BOOKING_SRV' > src/app/services/booking.service.ts
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
BOOKING_SRV

# Components
cat << 'BRANCH_SEL' > src/app/components/branch-selector/branch-selector.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-branch-selector', standalone: true, template: \`<div class="branch-selector">Selector de sucursal</div>\` })
export class BranchSelectorComponent {}
BRANCH_SEL

cat << 'ROOMS_SEC' > src/app/components/rooms-section/rooms-section.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-rooms-section', standalone: true, template: \`<div class="rooms-section">Catálogo de Suites</div>\` })
export class RoomsSectionComponent {}
ROOMS_SEC

cat << 'EXP_SEC' > src/app/components/experiences-section/experiences-section.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-experiences-section', standalone: true, template: \`<div class="experiences-section">Experiencias</div>\` })
export class ExperiencesSectionComponent {}
EXP_SEC

cat << 'BOOKING_MODAL' > src/app/components/booking-modal/booking-modal.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-booking-modal', standalone: true, template: \`<div class="modal">Modal Reserva</div>\` })
export class BookingModalComponent {}
BOOKING_MODAL

cat << 'AUTH_MODAL' > src/app/components/auth-modal/auth-modal.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-auth-modal', standalone: true, template: \`<div class="modal">Modal Login</div>\` })
export class AuthModalComponent {}
AUTH_MODAL

cat << 'SUITE_DETAIL' > src/app/pages/suite-detail/suite-detail.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-suite-detail', standalone: true, template: \`<div class="page">Detalle Suite</div>\` })
export class SuiteDetailComponent {}
SUITE_DETAIL

cat << 'MY_BOOKINGS' > src/app/pages/my-bookings/my-bookings.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-my-bookings', standalone: true, template: \`<div class="page">Mis Reservas</div>\` })
export class MyBookingsComponent {}
MY_BOOKINGS

cat << 'ADMIN_DASH' > src/app/pages/admin-dashboard/admin-dashboard.component.ts
import { Component } from '@angular/core';
@Component({ selector: 'app-admin-dashboard', standalone: true, template: \`<div class="page">Admin Dashboard</div>\` })
export class AdminDashboardComponent {}
ADMIN_DASH
