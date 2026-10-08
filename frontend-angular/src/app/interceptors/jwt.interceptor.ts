import { HttpInterceptorFn } from '@angular/common/http';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  // Ahora usamos cookies HttpOnly, por lo que no enviamos el token manualmente
  // Pero necesitamos asegurarnos de enviar las cookies (withCredentials: true)
  const cloned = req.clone({
    withCredentials: true
  });
  return next(cloned);
};
