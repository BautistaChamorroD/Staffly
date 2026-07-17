import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';

/**
 * RF-01: mientras el usuario tenga pendiente el cambio forzado de
 * contraseña (primer login con contraseña temporal), no puede navegar al
 * resto de la app — toda ruta protegida redirige a /change-password.
 */
export const forcePasswordChangeGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.mustChangePassword() ? router.createUrlTree(['/change-password']) : true;
};
