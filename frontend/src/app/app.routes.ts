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
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./core/change-password/change-password.component').then((m) => m.ChangePasswordComponent),
  },
  {
    path: '',
    canActivate: [authGuard, forcePasswordChangeGuard],
    loadComponent: () =>
      import('./core/shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./core/home/home.component').then((m) => m.HomeComponent),
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
        path: 'employees',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR'])],
        loadComponent: () =>
          import('./features/employees/components/employees-list/employees-list.component').then(
            (m) => m.EmployeesListComponent,
          ),
      },
      {
        path: 'availability',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE'])],
        loadComponent: () =>
          import('./features/availability/components/availability-list/availability-list.component').then(
            (m) => m.AvailabilityListComponent,
          ),
      },
      {
        path: 'schedules',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE'])],
        loadComponent: () =>
          import('./features/schedules/components/schedule-builder/schedule-builder.component').then(
            (m) => m.ScheduleBuilderComponent,
          ),
      },
      {
        path: 'leaves',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'SUPERVISOR', 'EMPLOYEE'])],
        loadComponent: () =>
          import('./features/leaves/components/leaves-list/leaves-list.component').then(
            (m) => m.LeavesListComponent,
          ),
      },
      {
        path: 'payroll/config',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/payroll/components/payroll-config/payroll-config.component').then(
            (m) => m.PayrollConfigComponent,
          ),
      },
      {
        path: 'payroll/periods',
        canActivate: [roleGuard(['ADMIN', 'RRHH'])],
        loadComponent: () =>
          import('./features/payroll/components/payroll-periods/payroll-periods.component').then(
            (m) => m.PayrollPeriodsComponent,
          ),
      },
      {
        path: 'advances',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'EMPLOYEE'])],
        loadComponent: () =>
          import('./features/advances/components/advances-list/advances-list.component').then(
            (m) => m.AdvancesListComponent,
          ),
      },
      {
        path: 'payslips',
        canActivate: [roleGuard(['ADMIN', 'RRHH', 'EMPLOYEE'])],
        loadComponent: () =>
          import('./features/payslips/components/payslips-list/payslips-list.component').then(
            (m) => m.PayslipsListComponent,
          ),
      },
      {
        path: 'reports',
        canActivate: [roleGuard(['ADMIN', 'RRHH'])],
        loadComponent: () =>
          import('./features/reports/components/reports-hub/reports-hub.component').then(
            (m) => m.ReportsHubComponent,
          ),
      },
      {
        path: 'reports/hours-worked',
        canActivate: [roleGuard(['ADMIN', 'RRHH'])],
        loadComponent: () =>
          import('./features/reports/components/hours-worked/hours-worked.component').then(
            (m) => m.HoursWorkedComponent,
          ),
      },
      {
        path: 'reports/payroll-cost',
        canActivate: [roleGuard(['ADMIN', 'RRHH'])],
        loadComponent: () =>
          import('./features/reports/components/payroll-cost/payroll-cost.component').then(
            (m) => m.PayrollCostComponent,
          ),
      },
      {
        path: 'reports/pending-advances',
        canActivate: [roleGuard(['ADMIN', 'RRHH'])],
        loadComponent: () =>
          import('./features/reports/components/pending-advances/pending-advances.component').then(
            (m) => m.PendingAdvancesComponent,
          ),
      },
      {
        path: 'audit-log',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/audit/components/audit-log/audit-log.component').then(
            (m) => m.AuditLogComponent,
          ),
      },
    ],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
