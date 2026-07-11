import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';
import { Rol } from '../models/rol';

/**
 * El rol se lee siempre del JWT (AuthService.getRole()), nunca de la URL ni
 * de un parámetro propio — regla dura de frontend/CLAUDE.md.
 */
export function roleGuard(rolesPermitidos: Rol[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      return router.createUrlTree(['/login']);
    }

    const rol = authService.getRole();
    return rol !== null && rolesPermitidos.includes(rol) ? true : router.createUrlTree(['/']);
  };
}
