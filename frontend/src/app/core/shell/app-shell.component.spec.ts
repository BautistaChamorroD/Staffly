import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';

import { AuthService } from '../services/auth.service';
import { AppShellComponent } from './app-shell.component';

function setup(role: string) {
  const routerStub = { navigate: vi.fn() };
  const authServiceStub = { getRole: vi.fn().mockReturnValue(role), logout: vi.fn() };

  TestBed.configureTestingModule({
    imports: [AppShellComponent, RouterTestingModule],
    providers: [
      { provide: AuthService, useValue: authServiceStub },
      { provide: Router, useValue: routerStub },
    ],
  });

  return { routerStub, authServiceStub };
}

describe('AppShellComponent', () => {
  it('should create', () => {
    setup('ADMIN');
    expect(TestBed.createComponent(AppShellComponent).componentInstance).toBeTruthy();
  });

  it('ADMIN navItems include Empleados, Reportes, Auditoría', () => {
    setup('ADMIN');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    const labels = comp.navItems.map((i) => i.label);
    expect(labels).toContain('Empleados');
    expect(labels).toContain('Reportes');
    expect(labels).toContain('Auditoría');
  });

  it('EMPLOYEE navItems include Mi horario but not Reportes or Empleados', () => {
    setup('EMPLOYEE');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    const labels = comp.navItems.map((i) => i.label);
    expect(labels).toContain('Mi horario');
    expect(labels).not.toContain('Reportes');
    expect(labels).not.toContain('Empleados');
  });

  it('SUPERVISOR navItems include Turnos but not Nómina or Reportes', () => {
    setup('SUPERVISOR');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    const labels = comp.navItems.map((i) => i.label);
    expect(labels).toContain('Turnos');
    expect(labels).not.toContain('Nómina');
    expect(labels).not.toContain('Reportes');
  });

  it('toggleDrawer flips drawerOpen', () => {
    setup('ADMIN');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    expect(comp.drawerOpen).toBe(false);
    comp.toggleDrawer();
    expect(comp.drawerOpen).toBe(true);
    comp.toggleDrawer();
    expect(comp.drawerOpen).toBe(false);
  });

  it('closeDrawer sets drawerOpen to false', () => {
    setup('ADMIN');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    comp.drawerOpen = true;
    comp.closeDrawer();
    expect(comp.drawerOpen).toBe(false);
  });

  it('logout calls authService.logout and navigates to /login', () => {
    const { routerStub, authServiceStub } = setup('ADMIN');
    const comp = TestBed.createComponent(AppShellComponent).componentInstance;
    comp.logout();
    expect(authServiceStub.logout).toHaveBeenCalled();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/login']);
  });
});
