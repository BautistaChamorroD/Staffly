import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  standalone: true,
  templateUrl: './home.component.html',
})
export class HomeComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  readonly role = this.authService.getRole();

  ngOnInit(): void {
    if (this.role === 'SUPER_ADMIN') {
      this.router.navigate(['/companies']);
    } else if (this.role === 'ADMIN' || this.role === 'RRHH' || this.role === 'SUPERVISOR') {
      this.router.navigate(['/employees']);
    }
    // EMPLOYEE stays — sees welcome content below
  }
}
