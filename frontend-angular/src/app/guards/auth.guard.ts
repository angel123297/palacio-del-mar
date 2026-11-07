import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router, UrlSegment } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Redirige al home preservando la URL destino como returnUrl
 * para que el login devuelva al usuario a donde iba (L3).
 */
function redirectHome(router: Router, returnUrl?: string) {
  return returnUrl
    ? router.createUrlTree(['/'], { queryParams: { returnUrl } })
    : router.createUrlTree(['/']);
}

function isAdmin(auth: AuthService): boolean {
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && role === 'admin';
}

function isHost(auth: AuthService): boolean {
  const role = (auth.currentUserValue?.role || '').toLowerCase();
  return auth.isLoggedIn() && (role === 'host' || role === 'anfitrion');
}

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : redirectHome(inject(Router), state.url);
};

export const authMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const target = '/' + segments.map((s: UrlSegment) => s.path).join('/');
  return auth.isLoggedIn() ? true : redirectHome(inject(Router), target);
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return isAdmin(auth) ? true : redirectHome(inject(Router), state.url);
};

export const adminMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const target = '/' + segments.map((s: UrlSegment) => s.path).join('/');
  return isAdmin(auth) ? true : redirectHome(inject(Router), target);
};

export const hostGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return isHost(auth) ? true : redirectHome(inject(Router), state.url);
};

export const hostMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const target = '/' + segments.map((s: UrlSegment) => s.path).join('/');
  return isHost(auth) ? true : redirectHome(inject(Router), target);
};
