import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ButtonDirective } from '../../shared/components/button/button.directive';
import { CardComponent } from '../../shared/components/card/card.component';
import { InputComponent } from '../../shared/components/input/input.component';

/**
 * Valida a nivel de grupo que newPassword y confirmPassword coincidan —
 * cross-field, no puede vivir en un control individual.
 */
function passwordsMatchValidator(group: AbstractControl): ValidationErrors | null {
  const nueva = group.get('newPassword')?.value;
  const confirmacion = group.get('confirmPassword')?.value;
  return nueva && confirmacion && nueva !== confirmacion ? { passwordsMismatch: true } : null;
}

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, CardComponent, InputComponent, ButtonDirective],
  templateUrl: './change-password.component.html',
})
export class ChangePasswordComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  form = this.fb.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: passwordsMatchValidator },
  );

  isLoading = false;
  errorMessage: string | null = null;

  get currentPasswordCtrl() {
    return this.form.get('currentPassword')!;
  }

  get newPasswordCtrl() {
    return this.form.get('newPassword')!;
  }

  get confirmPasswordCtrl() {
    return this.form.get('confirmPassword')!;
  }

  get currentPasswordErrorMessage(): string | undefined {
    return this.currentPasswordCtrl.invalid && this.currentPasswordCtrl.touched
      ? 'La contraseña actual es requerida.'
      : undefined;
  }

  get newPasswordErrorMessage(): string | undefined {
    if (!this.newPasswordCtrl.invalid || !this.newPasswordCtrl.touched) {
      return undefined;
    }
    if (this.newPasswordCtrl.errors?.['required']) {
      return 'La contraseña nueva es requerida.';
    }
    if (this.newPasswordCtrl.errors?.['minlength']) {
      return 'La contraseña nueva debe tener al menos 8 caracteres.';
    }
    return undefined;
  }

  get confirmPasswordErrorMessage(): string | undefined {
    if (!this.confirmPasswordCtrl.touched) {
      return undefined;
    }
    if (this.confirmPasswordCtrl.errors?.['required']) {
      return 'Repetí la contraseña nueva.';
    }
    if (this.form.errors?.['passwordsMismatch']) {
      return 'Las contraseñas no coinciden.';
    }
    return undefined;
  }

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = null;

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.authService
      .changePassword(currentPassword!, newPassword!)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isLoading = false;
          this.router.navigate(['/']);
        },
        error: (error: HttpErrorResponse) => {
          this.isLoading = false;
          this.errorMessage =
            error.status === 401
              ? 'La contraseña actual es incorrecta.'
              : 'No se pudo cambiar la contraseña. Intentá de nuevo.';
        },
      });
  }
}
