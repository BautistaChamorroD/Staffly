import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let routerStub: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    routerStub = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerStub },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds the bearer token to authenticated requests', () => {
    authService.setTokens('access-1', 'refresh-1');
    http.get('/api/v1/branches').subscribe();

    const req = httpMock.expectOne('/api/v1/branches');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('refreshes once and retries the request on a 401', () => {
    authService.setTokens('expired-access', 'refresh-1');
    const results: unknown[] = [];
    http.get('/api/v1/branches').subscribe((body) => results.push(body));

    httpMock.expectOne('/api/v1/branches').flush(null, { status: 401, statusText: 'Unauthorized' });

    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(refreshReq.request.body).toEqual({ refreshToken: 'refresh-1' });
    refreshReq.flush({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 1800 });

    const retriedReq = httpMock.expectOne('/api/v1/branches');
    expect(retriedReq.request.headers.get('Authorization')).toBe('Bearer access-2');
    retriedReq.flush(['ok']);

    expect(results).toEqual([['ok']]);
  });

  it('shares a single refresh between concurrent 401s instead of firing one refresh per request', () => {
    authService.setTokens('expired-access', 'refresh-1');
    const results: unknown[] = [];
    http.get('/api/v1/branches').subscribe((body) => results.push(body));
    http.get('/api/v1/employees').subscribe((body) => results.push(body));

    httpMock.expectOne('/api/v1/branches').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/v1/employees').flush(null, { status: 401, statusText: 'Unauthorized' });

    // exactamente UN refresh para los dos 401 — con un refresh por request,
    // la rotación del backend revoca el token del segundo y desloguea al usuario
    const refreshReq = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    refreshReq.flush({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresIn: 1800 });

    const retriedBranches = httpMock.expectOne('/api/v1/branches');
    expect(retriedBranches.request.headers.get('Authorization')).toBe('Bearer access-2');
    retriedBranches.flush(['branches']);

    const retriedEmployees = httpMock.expectOne('/api/v1/employees');
    expect(retriedEmployees.request.headers.get('Authorization')).toBe('Bearer access-2');
    retriedEmployees.flush(['employees']);

    expect(results).toEqual([['branches'], ['employees']]);
    expect(authService.getRefreshToken()).toBe('refresh-2');
  });

  it('clears tokens and redirects to login when the refresh itself fails', () => {
    authService.setTokens('expired-access', 'refresh-1');
    const errors: unknown[] = [];
    http.get('/api/v1/branches').subscribe({ error: (error) => errors.push(error) });

    httpMock.expectOne('/api/v1/branches').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(errors.length).toBe(1);
    expect(authService.getAccessToken()).toBeNull();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('allows a new refresh attempt after a previous refresh failed', () => {
    authService.setTokens('expired-access', 'refresh-1');
    http.get('/api/v1/branches').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/v1/branches').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock
      .expectOne(`${environment.apiUrl}/auth/refresh`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    // sesión nueva: el próximo 401 debe poder disparar un refresh fresco
    authService.setTokens('expired-access-2', 'refresh-3');
    http.get('/api/v1/employees').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/v1/employees').flush(null, { status: 401, statusText: 'Unauthorized' });

    const secondRefresh = httpMock.expectOne(`${environment.apiUrl}/auth/refresh`);
    expect(secondRefresh.request.body).toEqual({ refreshToken: 'refresh-3' });
    secondRefresh.flush({ accessToken: 'access-4', refreshToken: 'refresh-4', expiresIn: 1800 });
    httpMock.expectOne('/api/v1/employees').flush([]);
  });
});
