import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { LoginResponse } from '../models/auth';
import { AuthService } from './auth.service';

function loginResponse(debeCambiarPassword: boolean): LoginResponse {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    expiresIn: 1800,
    user: { id: 'u1', email: 'admin@staffly.test', rol: 'ADMIN', debeCambiarPassword },
  };
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('records that the user must change their password after login', () => {
    service.login({ email: 'admin@staffly.test', password: 'x' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse(true));
    expect(service.mustChangePassword()).toBe(true);
  });

  it('records no pending password change when the flag comes back false', () => {
    service.login({ email: 'admin@staffly.test', password: 'x' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse(false));
    expect(service.mustChangePassword()).toBe(false);
  });

  it('posts to /auth/change-password and clears the pending flag on success', () => {
    service.login({ email: 'admin@staffly.test', password: 'x' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse(true));

    service.changePassword('vieja', 'NuevaPassword123').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/auth/change-password`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ currentPassword: 'vieja', newPassword: 'NuevaPassword123' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(service.mustChangePassword()).toBe(false);
  });

  it('clears the pending flag on logout', () => {
    service.login({ email: 'admin@staffly.test', password: 'x' }).subscribe();
    httpMock.expectOne(`${environment.apiUrl}/auth/login`).flush(loginResponse(true));

    service.logout();
    httpMock.expectOne(`${environment.apiUrl}/auth/logout`).flush(null);

    expect(service.mustChangePassword()).toBe(false);
  });
});
