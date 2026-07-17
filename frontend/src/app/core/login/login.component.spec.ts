import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LoginResponse } from '../models/auth';
import { AuthService } from '../services/auth.service';
import { LoginComponent } from './login.component';

function loginResponse(debeCambiarPassword: boolean): LoginResponse {
  return {
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    expiresIn: 1800,
    user: { id: 'u1', email: 'admin@staffly.test', rol: 'ADMIN', debeCambiarPassword },
  };
}

describe('LoginComponent', () => {
  let authServiceStub: { login: ReturnType<typeof vi.fn> };
  let routerStub: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceStub = { login: vi.fn() };
    routerStub = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  function submitLogin() {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      email: 'admin@staffly.test',
      password: 'Password123',
    });
    fixture.componentInstance.onSubmit();
    fixture.detectChanges();
    return fixture;
  }

  it('navigates to home after a normal login', () => {
    authServiceStub.login.mockReturnValue(of(loginResponse(false)));
    submitLogin();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/']);
  });

  it('navigates to /change-password when the user must change their password', () => {
    authServiceStub.login.mockReturnValue(of(loginResponse(true)));
    submitLogin();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/change-password']);
  });

  it('shows an error message on invalid credentials', () => {
    authServiceStub.login.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const fixture = submitLogin();
    expect(routerStub.navigate).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage).toBe('Email o contraseña incorrectos.');
  });
});
