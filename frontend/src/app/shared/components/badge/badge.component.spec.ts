import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { BadgeComponent, BadgeVariant } from './badge.component';

@Component({
  standalone: true,
  imports: [BadgeComponent],
  template: `<ui-badge [variant]="variant">{{ label }}</ui-badge>`,
})
class HostComponent {
  variant: BadgeVariant = 'neutral';
  label = 'Activo';
}

describe('BadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('renders projected content', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.textContent?.trim()).toBe('Activo');
  });

  it('applies neutral classes by default', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-badge-neutral-bg');
  });

  it('applies success classes when variant is success', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'success';
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-badge-success-bg');
  });

  it('applies accent classes using the brand accent-soft tokens', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'accent';
    fixture.detectChanges();
    const span = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(span.className).toContain('bg-brand-acc-soft');
  });
});
