import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { Rol } from '../models/rol';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  function run(opts: {
    isLoggedIn: boolean;
    getRole: Rol | null;
    rolesPermitidos: Rol[];
  }): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            isLoggedIn: () => opts.isLoggedIn,
            getRole: () => opts.getRole,
          },
        },
      ],
    });
    return TestBed.runInInjectionContext(() =>
      roleGuard(opts.rolesPermitidos)({} as never, {} as never),
    ) as boolean | UrlTree;
  }

  it('allows navigation when the user has an authorized role', () => {
    const result = run({ isLoggedIn: true, getRole: 'ADMIN', rolesPermitidos: ['ADMIN', 'RRHH'] });
    expect(result).toBe(true);
  });

  it('redirects to /forbidden when the user has an unauthorized role', () => {
    const result = run({ isLoggedIn: true, getRole: 'EMPLOYEE', rolesPermitidos: ['ADMIN', 'RRHH'] });
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.createUrlTree(['/forbidden']));
  });

  it('redirects to /forbidden when the user has no role (null JWT)', () => {
    const result = run({ isLoggedIn: true, getRole: null, rolesPermitidos: ['ADMIN'] });
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.createUrlTree(['/forbidden']));
  });

  it('redirects to /login when the user is not logged in', () => {
    const result = run({ isLoggedIn: false, getRole: null, rolesPermitidos: ['ADMIN'] });
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.createUrlTree(['/login']));
  });
});
