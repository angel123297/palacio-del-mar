import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.getToken() && auth.currentUserValue ? true : inject(Router).createUrlTree(['/']);
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.getToken() && role === 'admin' ? true : inject(Router).createUrlTree(['/']);
};
