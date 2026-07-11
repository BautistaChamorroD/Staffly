import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { JwtClaims, LoginRequest, LoginResponse, RefreshResponse } from '../models/auth';
import { Rol } from '../models/rol';

/**
 * accessToken y refreshToken viven únicamente en memoria (nunca en
 * localStorage/sessionStorage) — máxima protección contra robo de token
 * vía XSS, a costa de perder la sesión en un F5 o cierre de pestaña.
 * Decisión tomada explícitamente con el usuario en FE-1.2.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private accessToken: string | null = null;
  private refreshToken: string | null = null;

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, credentials)
      .pipe(tap((response) => this.setTokens(response.accessToken, response.refreshToken)));
  }

  refreshAccessToken(): Observable<RefreshResponse> {
    return this.http
      .post<RefreshResponse>(`${environment.apiUrl}/auth/refresh`, {
        refreshToken: this.refreshToken,
      })
      .pipe(tap((response) => this.setTokens(response.accessToken, response.refreshToken)));
  }

  logout(): void {
    if (this.refreshToken) {
      this.http
        .post(`${environment.apiUrl}/auth/logout`, { refreshToken: this.refreshToken })
        .subscribe({ error: () => undefined });
    }
    this.clearTokens();
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  getRefreshToken(): string | null {
    return this.refreshToken;
  }

  setTokens(accessToken: string, refreshToken: string): void {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  clearTokens(): void {
    this.accessToken = null;
    this.refreshToken = null;
  }

  isLoggedIn(): boolean {
    return this.accessToken !== null;
  }

  getRole(): Rol | null {
    return this.decodeAccessToken()?.role ?? null;
  }

  getCompanyId(): string | null {
    return this.decodeAccessToken()?.company_id ?? null;
  }

  private decodeAccessToken(): JwtClaims | null {
    if (!this.accessToken) {
      return null;
    }
    return decodeJwtPayload<JwtClaims>(this.accessToken);
  }
}

/**
 * Decodifica el payload (segundo segmento) de un JWT en base64url. Solo
 * lectura de claims ya validados por el backend — nunca se verifica firma
 * acá, el frontend confía en lo que el backend firmó.
 */
function decodeJwtPayload<T>(token: string): T | null {
  const payload = token.split('.')[1];
  if (!payload) {
    return null;
  }
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  try {
    return JSON.parse(atob(base64)) as T;
  } catch {
    return null;
  }
}
