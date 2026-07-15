import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

/**
 * Placeholder temporal de la ruta protegida raíz para roles sin feature
 * propia todavía. Super Admin redirige a /companies (FE-1.4); el resto de
 * los roles sigue viendo este placeholder hasta que existan sus features
 * (branches/employees en FE-1.5/1.6).
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
    }
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
