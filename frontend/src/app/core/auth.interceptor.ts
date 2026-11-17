import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const PUBLIC = ['/auth/login', '/auth/refresh'];

function withToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}

/** Gắn Bearer token; khi 401 thử refresh đúng một lần rồi gửi lại, thất bại thì đăng xuất. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  if (PUBLIC.some((p) => req.url.includes(p))) {
    return next(req);
  }
  return next(withToken(req, auth.token())).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || !auth.isLoggedIn()) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((token) => next(withToken(req, token))),
        catchError((e) => {
          auth.logout();
          return throwError(() => e);
        }),
      );
    }),
  );
};
