import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/']);
};

export const authMatchGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/']);
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && role === 'admin' ? true : inject(Router).createUrlTree(['/']);
};

export const adminMatchGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && role === 'admin' ? true : inject(Router).createUrlTree(['/']);
};

export const hostGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && (role === 'host' || role === 'admin') ? true : inject(Router).createUrlTree(['/']);
};

export const hostMatchGuard: CanMatchFn = () => {
  const auth = inject(AuthService);
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && (role === 'host' || role === 'admin') ? true : inject(Router).createUrlTree(['/']);
};
