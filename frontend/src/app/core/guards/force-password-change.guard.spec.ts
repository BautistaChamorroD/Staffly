import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { forcePasswordChangeGuard } from './force-password-change.guard';

describe('forcePasswordChangeGuard', () => {
  function run(mustChangePassword: boolean): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { mustChangePassword: () => mustChangePassword } }],
    });
    return TestBed.runInInjectionContext(() =>
      forcePasswordChangeGuard({} as never, {} as never),
    ) as boolean | UrlTree;
  }

  it('redirects to /change-password when a password change is pending', () => {
    const result = run(true);
    const router = TestBed.inject(Router);
    expect(result).toEqual(router.createUrlTree(['/change-password']));
  });

  it('allows navigation when no password change is pending', () => {
    expect(run(false)).toBe(true);
  });
});
