import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ForbiddenComponent } from './forbidden.component';

describe('ForbiddenComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ForbiddenComponent],
      providers: [provideRouter([])],
    });
  });

  it('should create', () => {
    expect(TestBed.createComponent(ForbiddenComponent).componentInstance).toBeTruthy();
  });

  it('renders "403" or "sin permisos" text', () => {
    const fixture = TestBed.createComponent(ForbiddenComponent);
    fixture.detectChanges();
    const text = (fixture.nativeElement.textContent as string).toLowerCase();
    expect(text).toMatch(/403|permiso/i);
  });

  it('renders link to home', () => {
    const fixture = TestBed.createComponent(ForbiddenComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/"]') as HTMLAnchorElement | null;
    expect(link).toBeTruthy();
  });
});
