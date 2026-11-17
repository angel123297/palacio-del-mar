import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router, UrlSegment } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Redirige al home preservando la URL destino completa como returnUrl
 * para que el login devuelva al usuario a donde iba.
 */
function redirectHome(router: Router, returnUrl?: string) {
  return returnUrl
    ? router.createUrlTree(['/'], { queryParams: { returnUrl } })
    : router.createUrlTree(['/']);
}

function resolveTargetUrl(router: Router, segments: UrlSegment[]): string {
  const currentNav = router.getCurrentNavigation();
  if (currentNav?.extractedUrl) {
    return currentNav.extractedUrl.toString();
  }
  return '/' + segments.map((s: UrlSegment) => s.path).join('/');
}

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map(user => (user && user.emailVerified === true) ? true : redirectHome(router, state.url))
  );
};

export const authMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const target = resolveTargetUrl(router, segments);
  return auth.ensureSession().pipe(
    map(user => (user && user.emailVerified === true) ? true : redirectHome(router, target))
  );
};

export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map(user => (user && user.emailVerified === true && (user.role || '').toLowerCase() === 'admin')
      ? true
      : redirectHome(router, state.url))
  );
};

export const adminMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const target = resolveTargetUrl(router, segments);
  return auth.ensureSession().pipe(
    map(user => (user && user.emailVerified === true && (user.role || '').toLowerCase() === 'admin')
      ? true
      : redirectHome(router, target))
  );
};

export const hostGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.ensureSession().pipe(
    map(user => {
      const role = (user?.role || '').toLowerCase();
      return (user && user.emailVerified === true && (role === 'host' || role === 'anfitrion' || role === 'admin'))
        ? true
        : redirectHome(router, state.url);
    })
  );
};

export const hostMatchGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const target = resolveTargetUrl(router, segments);
  return auth.ensureSession().pipe(
    map(user => {
      const role = (user?.role || '').toLowerCase();
      return (user && user.emailVerified === true && (role === 'host' || role === 'anfitrion' || role === 'admin'))
        ? true
        : redirectHome(router, target);
    })
  );
};
