import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { SuiteDetailComponent } from './pages/suite-detail/suite-detail.component';
import { MyBookingsComponent } from './pages/my-bookings/my-bookings.component';
import { AdminDashboardComponent } from './pages/admin-dashboard/admin-dashboard.component';
import { authGuard, adminGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'habitaciones/:idOrSlug', component: SuiteDetailComponent },
  { path: 'mis-reservas', component: MyBookingsComponent, canActivate: [authGuard] },
  { path: 'my-bookings', component: MyBookingsComponent, canActivate: [authGuard] },
  { path: 'reservas/:id', loadComponent: () => import('./pages/booking-detail/booking-detail.component').then(m => m.BookingDetailComponent), canActivate: [authGuard] },
  { path: 'reservas/:id/comprobante', loadComponent: () => import('./pages/receipt/receipt.component').then(m => m.ReceiptComponent), canActivate: [authGuard] },
  { path: 'admin', component: AdminDashboardComponent, canActivate: [adminGuard] },
  { path: 'checkout/:bookingId', loadComponent: () => import('./pages/checkout/checkout').then(m => m.CheckoutComponent), canActivate: [authGuard] },
  { path: 'perfil', loadComponent: () => import('./pages/profile/profile').then(m => m.ProfileComponent), canActivate: [authGuard] },
  { path: 'calendario', loadComponent: () => import('./pages/calendar/calendar').then(m => m.CalendarComponent) },
  { path: '**', redirectTo: '' }
];
