import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  const token = localStorage.getItem('token');
  let headers = req.headers;
  if (token && !headers.has('Authorization')) {
    headers = headers.set('Authorization', `Bearer ${token}`);
  }

  const cloned = req.clone({
    withCredentials: true,
    headers
  });

  return next(cloned).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        const isAuthEndpoint = req.url.includes('/auth/login') || 
                               req.url.includes('/auth/register') || 
                               req.url.includes('/auth/me') ||
                               req.url.includes('/auth/verify-email');
        if (!isAuthEndpoint && authService.isLoggedIn()) {
          authService.clearLocalData();
          const currentUrl = router.url;
          const isProtectedPage = ['/mis-reservas', '/reservas', '/checkout', '/admin', '/anfitrion', '/perfil'].some(p => currentUrl.startsWith(p));
          if (isProtectedPage) {
            router.navigate(['/']);
          }
        }
      }
      return throwError(() => error);
    })
  );
};
