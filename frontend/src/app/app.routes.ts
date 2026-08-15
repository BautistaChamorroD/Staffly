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
    path: 'availability',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/availability/components/availability-list/availability-list.component').then(
        (m) => m.AvailabilityListComponent,
      ),
  },
  {
    path: 'schedules',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/schedules/components/schedule-builder/schedule-builder.component').then(
        (m) => m.ScheduleBuilderComponent,
      ),
  },
  {
    path: 'payroll/config',
    canActivate: [roleGuard(['ADMIN']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/payroll/components/payroll-config/payroll-config.component').then(
        (m) => m.PayrollConfigComponent,
      ),
  },
  {
    path: 'payroll/periods',
    canActivate: [roleGuard(['ADMIN', 'RRHH']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/payroll/components/payroll-periods/payroll-periods.component').then(
        (m) => m.PayrollPeriodsComponent,
      ),
  },
  {
    path: 'advances',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'EMPLOYEE']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/advances/components/advances-list/advances-list.component').then(
        (m) => m.AdvancesListComponent,
      ),
  },
  {
    path: 'payslips',
    canActivate: [roleGuard(['ADMIN', 'RRHH', 'EMPLOYEE']), forcePasswordChangeGuard],
    loadComponent: () =>
      import('./features/payslips/components/payslips-list/payslips-list.component').then(
        (m) => m.PayslipsListComponent,
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
