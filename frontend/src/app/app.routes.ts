import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./core/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'companies',
    canActivate: [roleGuard(['SUPER_ADMIN'])],
    loadComponent: () =>
      import('./features/companies/components/companies-list/companies-list.component').then(
        (m) => m.CompaniesListComponent,
      ),
  },
  {
    path: 'branches',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])],
    loadComponent: () =>
      import('./features/branches/components/branches-list/branches-list.component').then(
        (m) => m.BranchesListComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: '**',
    redirectTo: '',
  },
];
