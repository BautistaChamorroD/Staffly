import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

const PUBLIC_PATHS = ['/auth/login', '/auth/refresh'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isPublic = PUBLIC_PATHS.some((path) => req.url.includes(path));
  const accessToken = authService.getAccessToken();

  const authorizedReq =
    !isPublic && accessToken
      ? req.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
      : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      if (isPublic || !isUnauthorized || !authService.getRefreshToken()) {
        return throwError(() => error);
      }

      return authService.refreshAccessToken().pipe(
        switchMap(() => {
          const retriedReq = req.clone({
            setHeaders: { Authorization: `Bearer ${authService.getAccessToken()}` },
          });
          return next(retriedReq);
        }),
        catchError((refreshError: unknown) => {
          authService.clearTokens();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};
