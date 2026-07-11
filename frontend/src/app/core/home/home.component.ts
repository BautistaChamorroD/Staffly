import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Placeholder temporal de la ruta protegida raíz. Se reemplaza por un
 * dashboard real cuando existan features (FE-1.4 en adelante) — por ahora
 * solo confirma que la sesión y el guard de rol funcionan end-to-end.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
})
export class HomeComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  rol = this.authService.getRole();
  companyId = this.authService.getCompanyId();

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
