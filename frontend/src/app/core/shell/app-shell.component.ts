import { Component, HostListener, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Rol } from '../models/rol';
import { AuthService } from '../services/auth.service';

interface NavItem {
  label: string;
  route: string;
  icon: string;
}

const ICONS = {
  users:    'M15 19.128a9.38 9.38 0 002.625.372 9.337 9.337 0 004.121-.952 4.125 4.125 0 00-7.533-2.493M15 19.128v-.003c0-1.113-.285-2.16-.786-3.07M15 19.128v.106A12.318 12.318 0 018.624 21c-2.331 0-4.512-.645-6.374-1.766l-.001-.109a6.375 6.375 0 0111.964-3.07M12 6.375a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zm8.25 2.25a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z',
  building: 'M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21',
  calendar: 'M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 012.25-2.25h13.5A2.25 2.25 0 0121 7.5v11.25m-18 0A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75m-18 0v-7.5A2.25 2.25 0 015.25 9h13.5A2.25 2.25 0 0121 11.25v7.5',
  clock:    'M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z',
  document: 'M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z',
  currency: 'M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  chart:    'M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z',
  shield:   'M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z',
  cog:      'M10.5 6h9.75M10.5 6a1.5 1.5 0 11-3 0m3 0a1.5 1.5 0 10-3 0M3.75 6H7.5m3 12h9.75m-9.75 0a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m-3.75 0H7.5m9-6h3.75m-3.75 0a1.5 1.5 0 01-3 0m3 0a1.5 1.5 0 00-3 0m-9.75 0h9.75',
  list:     'M8.25 6.75h12M8.25 12h12m-12 5.25h12M3.75 6.75h.007v.008H3.75V6.75zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zM3.75 12h.007v.008H3.75V12zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0zm-.375 5.25h.007v.008H3.75v-.008zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z',
} as const;

const NAV_ITEMS: Record<Rol, NavItem[]> = {
  SUPER_ADMIN: [
    { label: 'Empresas',       route: '/companies',      icon: ICONS.building },
  ],
  ADMIN: [
    { label: 'Empleados',      route: '/employees',      icon: ICONS.users    },
    { label: 'Sucursales',     route: '/branches',       icon: ICONS.building },
    { label: 'Turnos',         route: '/schedules',      icon: ICONS.calendar },
    { label: 'Disponibilidad', route: '/availability',   icon: ICONS.clock    },
    { label: 'Licencias',      route: '/leaves',         icon: ICONS.document },
    { label: 'Adelantos',      route: '/advances',       icon: ICONS.currency },
    { label: 'Recibos',        route: '/payslips',       icon: ICONS.document },
    { label: 'Nómina',         route: '/payroll/periods',icon: ICONS.list     },
    { label: 'Config. nómina', route: '/payroll/config', icon: ICONS.cog      },
    { label: 'Reportes',       route: '/reports',        icon: ICONS.chart    },
    { label: 'Auditoría',      route: '/audit-log',      icon: ICONS.shield   },
  ],
  RRHH: [
    { label: 'Empleados',      route: '/employees',      icon: ICONS.users    },
    { label: 'Sucursales',     route: '/branches',       icon: ICONS.building },
    { label: 'Turnos',         route: '/schedules',      icon: ICONS.calendar },
    { label: 'Disponibilidad', route: '/availability',   icon: ICONS.clock    },
    { label: 'Licencias',      route: '/leaves',         icon: ICONS.document },
    { label: 'Adelantos',      route: '/advances',       icon: ICONS.currency },
    { label: 'Recibos',        route: '/payslips',       icon: ICONS.document },
    { label: 'Nómina',         route: '/payroll/periods',icon: ICONS.list     },
    { label: 'Reportes',       route: '/reports',        icon: ICONS.chart    },
  ],
  SUPERVISOR: [
    { label: 'Empleados',      route: '/employees',      icon: ICONS.users    },
    { label: 'Sucursales',     route: '/branches',       icon: ICONS.building },
    { label: 'Turnos',         route: '/schedules',      icon: ICONS.calendar },
    { label: 'Disponibilidad', route: '/availability',   icon: ICONS.clock    },
    { label: 'Licencias',      route: '/leaves',         icon: ICONS.document },
  ],
  EMPLOYEE: [
    { label: 'Mi horario',     route: '/schedules',      icon: ICONS.calendar },
    { label: 'Disponibilidad', route: '/availability',   icon: ICONS.clock    },
    { label: 'Licencias',      route: '/leaves',         icon: ICONS.document },
    { label: 'Adelantos',      route: '/advances',       icon: ICONS.currency },
    { label: 'Mis recibos',    route: '/payslips',       icon: ICONS.document },
  ],
};

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-shell.component.html',
})
export class AppShellComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  drawerOpen = false;

  readonly role: Rol | null = this.authService.getRole();
  readonly navItems: NavItem[] = this.role ? (NAV_ITEMS[this.role] ?? []) : [];

  toggleDrawer(): void {
    this.drawerOpen = !this.drawerOpen;
  }

  closeDrawer(): void {
    this.drawerOpen = false;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeDrawer();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
