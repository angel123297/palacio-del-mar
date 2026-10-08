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
  { path: 'admin', component: AdminDashboardComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' }
];
