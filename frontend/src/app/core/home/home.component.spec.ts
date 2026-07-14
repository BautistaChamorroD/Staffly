import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  it('redirects a SUPER_ADMIN to /companies', async () => {
    const routerStub = { navigate: vi.fn() };
    const authServiceStub = {
      getRole: () => 'SUPER_ADMIN',
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

    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    expect(routerStub.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('does not redirect a non-SUPER_ADMIN role', async () => {
    const routerStub = { navigate: vi.fn() };
    const authServiceStub = {
      getRole: () => 'ADMIN',
      getCompanyId: () => 'company-1',
      logout: () => {},
    };

    TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: Router, useValue: routerStub },
      ],
    });

    const fixture = TestBed.createComponent(HomeComponent);
    await fixture.whenStable();

    expect(routerStub.navigate).not.toHaveBeenCalled();
  });
});
