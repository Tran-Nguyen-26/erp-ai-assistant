import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { LoginResponse, Role, UserResponse } from './models/auth.model';

const ACCESS = 'erpai.access';
const REFRESH = 'erpai.refresh';
const USER = 'erpai.user';

/** localStorage có thể bị chặn (chế độ riêng tư): mọi truy cập đều bọc try/catch. */
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    /* bỏ qua */
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly user = signal<UserResponse | null>(this.loadUser());

  token(): string | null {
    return read(ACCESS);
  }

  login(username: string, password: string): Observable<UserResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', { username, password }).pipe(
      tap((res) => this.store(res)),
      map((res) => res.user as UserResponse),
    );
  }

  /** Đổi refresh token lấy access token mới; trả về access token. */
  refresh(): Observable<string> {
    return this.http
      .post<LoginResponse>('/api/v1/auth/refresh', { refreshToken: read(REFRESH) })
      .pipe(
        tap((res) => this.store(res)),
        map((res) => res.accessToken),
      );
  }

  logout(): void {
    write(ACCESS, null);
    write(REFRESH, null);
    write(USER, null);
    this.user.set(null);
    void this.router.navigate(['/login']);
  }

  hasRole(...roles: Role[]): boolean {
    const u = this.user();
    return !!u && roles.includes(u.role);
  }

  isLoggedIn(): boolean {
    return !!this.token() && !!this.user();
  }

  private store(res: LoginResponse): void {
    write(ACCESS, res.accessToken);
    write(REFRESH, res.refreshToken);
    if (res.user) {
      write(USER, JSON.stringify(res.user));
      this.user.set(res.user);
    }
  }

  private loadUser(): UserResponse | null {
    const raw = read(USER);
    try {
      return raw ? (JSON.parse(raw) as UserResponse) : null;
    } catch {
      return null;
    }
  }
}
