import { Routes } from '@angular/router';
import { authGuard, adminGuard, hostGuard, authMatchGuard, adminMatchGuard, hostMatchGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent) },
  { path: 'buscar', loadComponent: () => import('./pages/search/search.component').then(m => m.SearchComponent) },
  { path: 'habitaciones/:idOrSlug', loadComponent: () => import('./pages/suite-detail/suite-detail.component').then(m => m.SuiteDetailComponent) },
  { path: 'mis-reservas', loadComponent: () => import('./pages/my-bookings/my-bookings.component').then(m => m.MyBookingsComponent), canMatch: [authMatchGuard], canActivate: [authGuard] },
  { path: 'my-bookings', redirectTo: 'mis-reservas', pathMatch: 'full' },
  { path: 'reservas/:id', loadComponent: () => import('./pages/booking-detail/booking-detail.component').then(m => m.BookingDetailComponent), canMatch: [authMatchGuard], canActivate: [authGuard] },
  { path: 'reservas/:id/comprobante', loadComponent: () => import('./pages/receipt/receipt.component').then(m => m.ReceiptComponent), canMatch: [authMatchGuard], canActivate: [authGuard] },
  { path: 'admin', loadComponent: () => import('./pages/admin-dashboard/admin-dashboard.component').then(m => m.AdminDashboardComponent), canMatch: [adminMatchGuard], canActivate: [adminGuard] },
  { path: 'anfitrion', loadComponent: () => import('./pages/host-dashboard/host-dashboard.component').then(m => m.HostDashboardComponent), canMatch: [hostMatchGuard], canActivate: [hostGuard] },
  { path: 'checkout/:bookingId', loadComponent: () => import('./pages/checkout/checkout').then(m => m.CheckoutComponent), canMatch: [authMatchGuard], canActivate: [authGuard] },
  { path: 'perfil', loadComponent: () => import('./pages/profile/profile').then(m => m.ProfileComponent), canMatch: [authMatchGuard], canActivate: [authGuard] },
  { path: 'calendario', loadComponent: () => import('./pages/calendar/calendar').then(m => m.CalendarComponent) },
  { path: 'verificar-email/:token', loadComponent: () => import('./pages/verify-email/verify-email').then(m => m.VerifyEmail) },
  { path: 'demo/external-hotel', loadComponent: () => import('./pages/external-hotel/external-hotel.component').then(m => m.ExternalHotelComponent) },
  { path: '**', loadComponent: () => import('./pages/not-found/not-found.component').then(m => m.NotFoundComponent) }
];

