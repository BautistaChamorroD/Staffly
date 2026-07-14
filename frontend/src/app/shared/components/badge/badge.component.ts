import { Component, Input } from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'error' | 'neutral' | 'accent';

@Component({
  selector: 'ui-badge',
  standalone: true,
  templateUrl: './badge.component.html',
})
export class BadgeComponent {
  @Input() variant: BadgeVariant = 'neutral';

  get variantClasses(): string {
    switch (this.variant) {
      case 'success':
        return 'bg-badge-success-bg text-badge-success-ink';
      case 'warning':
        return 'bg-badge-warning-bg text-badge-warning-ink';
      case 'error':
        return 'bg-badge-error-bg text-badge-error-ink';
      case 'accent':
        return 'bg-brand-acc-soft text-brand-acc-ink';
      case 'neutral':
        return 'bg-badge-neutral-bg text-badge-neutral-ink';
    }
  }
}
