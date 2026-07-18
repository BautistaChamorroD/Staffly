import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { ChangePasswordComponent } from './change-password.component';

describe('ChangePasswordComponent', () => {
  let authServiceStub: { changePassword: ReturnType<typeof vi.fn> };
  let routerStub: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authServiceStub = { changePassword: vi.fn().mockReturnValue(of(undefined)) };
    routerStub = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });
  });

  function fillForm(current: string, nueva: string, confirmacion: string) {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      currentPassword: current,
      newPassword: nueva,
      confirmPassword: confirmacion,
    });
    return fixture;
  }

  it('renders three password inputs, each with a label', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input[type="password"]');
    const labels = fixture.nativeElement.querySelectorAll('label');
    expect(inputs.length).toBe(3);
    expect(labels.length).toBe(3);
  });

  it('does not submit when the form is empty', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(authServiceStub.changePassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('rejects a new password shorter than 8 characters', () => {
    const fixture = fillForm('vieja123', 'corta', 'corta');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(authServiceStub.changePassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('al menos 8 caracteres');
  });

  it('rejects mismatched new password and confirmation', () => {
    const fixture = fillForm('vieja123', 'NuevaPassword123', 'OtraDistinta123');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(authServiceStub.changePassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Las contraseñas no coinciden.');
  });

  it('submits current and new password, then navigates to home', () => {
    const fixture = fillForm('vieja123', 'NuevaPassword123', 'NuevaPassword123');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(authServiceStub.changePassword).toHaveBeenCalledWith('vieja123', 'NuevaPassword123');
    expect(routerStub.navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows a wrong-current-password message on a 401', () => {
    authServiceStub.changePassword.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );
    const fixture = fillForm('incorrecta', 'NuevaPassword123', 'NuevaPassword123');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
    const alert = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('La contraseña actual es incorrecta.');
  });
});
