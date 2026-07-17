import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { HomeComponent } from './home.component';

function setup(role: string) {
  const routerStub = { navigate: vi.fn() };
  const authServiceStub = {
    getRole: () => role,
    getCompanyId: () => null,
    logout: () => {},
  };

  TestBed.configureTestingModule({
    imports: [HomeComponent],
    providers: [
      { provide: AuthService, useValue: authServiceStub },
      { provide: Router, useValue: routerStub },
    ],
  });

  return routerStub;
}

describe('HomeComponent', () => {
  it('redirects a SUPER_ADMIN to /companies', () => {
    const routerStub = setup('SUPER_ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('redirects an ADMIN to /employees', () => {
    const routerStub = setup('ADMIN');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('redirects an RRHH to /employees', () => {
    const routerStub = setup('RRHH');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('redirects a SUPERVISOR to /employees', () => {
    const routerStub = setup('SUPERVISOR');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('does not redirect an EMPLOYEE', () => {
    const routerStub = setup('EMPLOYEE');
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    expect(routerStub.navigate).not.toHaveBeenCalled();
  });
});
