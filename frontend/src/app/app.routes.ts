import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { forcePasswordChangeGuard } from './core/guards/force-password-change.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./core/login/login.component').then((m) => m.LoginComponent),
  },
  {
    // Solo authGuard, sin forcePasswordChangeGuard: es el destino del
    // redirect forzado, no puede redirigirse a sí misma.
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./core/change-password/change-password.component').then((m) => m.ChangePasswordComponent),
  },
  {
    path: 'companies',
    canActivate: [roleGuard(['SUPER_ADMIN']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/companies/components/companies-list/companies-list.component').then(
        (m) => m.CompaniesListComponent,
      ),
  },
  {
    path: 'branches',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/branches/components/branches-list/branches-list.component').then(
        (m) => m.BranchesListComponent,
      ),
  },
  {
    path: 'employees',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/employees/components/employees-list/employees-list.component').then(
        (m) => m.EmployeesListComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard, forcePasswordChangeGuard],
    loadComponent: () => import('./core/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
