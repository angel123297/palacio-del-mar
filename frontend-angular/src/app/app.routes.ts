import { Routes } from '@angular/router';
import { HomeComponent } from './pages/home/home.component';
import { SuiteDetailComponent } from './pages/suite-detail/suite-detail.component';
import { MyBookingsComponent } from './pages/my-bookings/my-bookings.component';
import { AdminDashboardComponent } from './pages/admin-dashboard/admin-dashboard.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'habitaciones/:idOrSlug', component: SuiteDetailComponent },
  { path: 'mis-reservas', component: MyBookingsComponent },
  { path: 'my-bookings', component: MyBookingsComponent },
  { path: 'admin', component: AdminDashboardComponent },
  { path: '**', redirectTo: '' }
];
