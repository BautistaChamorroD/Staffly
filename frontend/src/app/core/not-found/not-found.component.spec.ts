import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotFoundComponent } from './not-found.component';

describe('NotFoundComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotFoundComponent],
      providers: [provideRouter([])],
    });
  });

  it('should create', () => {
    expect(TestBed.createComponent(NotFoundComponent).componentInstance).toBeTruthy();
  });

  it('renders "404" heading', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('404');
  });

  it('renders link to home', () => {
    const fixture = TestBed.createComponent(NotFoundComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('a[href="/"]') as HTMLAnchorElement | null;
    expect(link).toBeTruthy();
  });
});
