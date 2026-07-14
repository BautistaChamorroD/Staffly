import { Directive, HostBinding, Input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary';
export type ButtonSize = 'default' | 'sm';

@Directive({
  selector: 'button[ui-button]',
  standalone: true,
})
export class ButtonDirective {
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: ButtonSize = 'default';

  @HostBinding('class')
  get hostClasses(): string {
    const base = 'rounded-lg font-semibold disabled:opacity-50 disabled:cursor-not-allowed';
    const variantClasses =
      this.variant === 'secondary'
        ? 'border border-brand-line bg-brand-card text-brand-ink hover:border-brand-muted'
        : 'bg-brand-acc text-white hover:bg-brand-acc-dark';
    const sizeClasses = this.size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-3.5 py-2 text-sm';
    return `${base} ${variantClasses} ${sizeClasses}`;
  }
}
