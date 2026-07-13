import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ButtonDirective, ButtonSize, ButtonVariant } from './button.directive';

@Component({
  standalone: true,
  imports: [ButtonDirective],
  template: `<button ui-button [variant]="variant" [size]="size" [disabled]="isDisabled">Enviar</button>`,
})
class HostComponent {
  variant: ButtonVariant = 'primary';
  size: ButtonSize = 'default';
  isDisabled = false;
}

describe('ButtonDirective', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('applies primary variant classes by default', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('bg-brand-acc');
  });

  it('applies secondary variant classes when variant is secondary', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.variant = 'secondary';
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('border-brand-line');
  });

  it('applies smaller padding classes when size is sm', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.size = 'sm';
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.className).toContain('text-xs');
  });

  it('leaves the native disabled attribute under the caller\'s control', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.isDisabled = true;
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });
});
