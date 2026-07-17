import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Placeholder temporal de la ruta protegida raíz — solo queda para EMPLOYEE,
 * que todavía no tiene pantalla propia. El resto de los roles redirige a su
 * feature principal apenas loguea.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
})
export class HomeComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  rol = this.authService.getRole();
  companyId = this.authService.getCompanyId();

  ngOnInit(): void {
    if (this.rol === 'SUPER_ADMIN') {
      this.router.navigate(['/companies']);
    } else if (this.rol === 'ADMIN' || this.rol === 'RRHH' || this.rol === 'SUPERVISOR') {
      this.router.navigate(['/employees']);
    }
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
