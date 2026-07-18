import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ButtonDirective } from '../../shared/components/button/button.directive';
import { CardComponent } from '../../shared/components/card/card.component';
import { InputComponent } from '../../shared/components/input/input.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, CardComponent, InputComponent, ButtonDirective],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  isLoading = false;
  errorMessage: string | null = null;

  get emailCtrl() {
    return this.form.get('email')!;
  }

  get passwordCtrl() {
    return this.form.get('password')!;
  }

  get emailErrorMessage(): string | undefined {
    if (!this.emailCtrl.invalid || !this.emailCtrl.touched) {
      return undefined;
    }
    if (this.emailCtrl.errors?.['required']) {
      return 'El email es requerido.';
    }
    if (this.emailCtrl.errors?.['email']) {
      return 'Ingresá un email válido.';
    }
    return undefined;
  }

  get passwordErrorMessage(): string | undefined {
    return this.passwordCtrl.invalid && this.passwordCtrl.touched
      ? 'La contraseña es requerida.'
      : undefined;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const { email, password } = this.form.getRawValue();
    this.authService
      .login({ email: email!, password: password! })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.isLoading = false;
          // RF-01: primer login con contraseña temporal → cambio forzado
          this.router.navigate(response.user.debeCambiarPassword ? ['/change-password'] : ['/']);
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading = false;
          this.errorMessage =
            error.status === 401
              ? 'Email o contraseña incorrectos.'
              : 'No se pudo iniciar sesión. Intentá de nuevo.';
        },
      });
  }
}
